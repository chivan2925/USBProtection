package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** USBGuard watch consumer. Never executes HTTP I/O on the daemon event reader. */
@Component
public class UsbGuardEventListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsbGuardEventListener.class);
    private static final int MAX_WATCH_LINE = 32768;
    private static final int MAX_RECENT_EVENTS = 1024;
    private static final long DEDUP_WINDOW_MILLIS = 1500;
    private static final long RECOVERY_AGE_MILLIS = 2500;
    private static final Pattern POLICY_ID = Pattern.compile("^\\[device\\] PolicyApplied: id=(\\d+)(?:\\s.*)?$");
    private static final Pattern POLICY_TARGET = Pattern.compile("(?m)^target_new=(allow|block|reject)$");

    @FunctionalInterface
    interface WatchProcessLauncher { Process start() throws IOException; }

    // Keep the original Insert target and durable-queue outcome separately from the
    // latest applied target. A failed enqueue must be retryable without inventing
    // a second physical insertion when USBGuard replays an Insert section.
    /** Immutable capture: retries must never create a new event ID or occur time. */
    private record PendingAudit(UsbDeviceInfo device, String eventType, String target,
                                ActiveUserInfo user, Instant occurredAt, String eventId) {
        static PendingAudit capture(UsbDeviceInfo device, String type, String target, ActiveUserInfo user) {
            return new PendingAudit(device, type, target, user, Instant.now(), UUID.randomUUID().toString());
        }
    }
    private record DeviceObservation(String identity, String target, ActiveUserInfo user,
                                     String insertionTarget, boolean insertionQueued, UsbDeviceInfo device,
                                     PendingAudit insertionAudit) {
        DeviceObservation withTarget(String effectiveTarget) {
            return new DeviceObservation(identity, effectiveTarget, user, insertionTarget, insertionQueued,
                    device, insertionAudit);
        }
        DeviceObservation queued() {
            return new DeviceObservation(identity, target, user, insertionTarget, true, device, insertionAudit);
        }
    }
    private record PendingInsert(String identity, String initialTarget, ActiveUserInfo user, long seenAt) {}
    private record PendingPolicy(UsbDeviceInfo device, String target, ActiveUserInfo user, PendingAudit audit) {}

    private final UsbGuardEventParser eventParser;
    private final UsbGuardPresenceParser presenceParser;
    private final ActiveUserResolver activeUserResolver;
    private final EndpointManager endpointManager;
    private final SystemInfoProvider systemInfoProvider;
    private final EventSenderService eventSenderService;
    private final DesktopNotificationService desktopNotificationService;
    private final UsbGuardGateway gateway;
    private final WatchProcessLauncher watchProcessLauncher;
    private final long reconnectDelayMillis;
    private final AtomicBoolean started = new AtomicBoolean();
    private final Map<String, Long> recent = new ConcurrentHashMap<>();
    private final Map<Integer, PendingInsert> pendingInserts = new ConcurrentHashMap<>();
    // Stable captured audit for final PolicyApplied when its durable write fails.
    private final Map<Integer, PendingAudit> pendingFinalAudits = new ConcurrentHashMap<>();
    // A DISCONNECTED audit write can fail even though the physical removal is final.
    // Keep its original identity/time in bounded memory and retry independently.
    private final ArrayDeque<PendingAudit> pendingRemovals = new ArrayDeque<>();
    // Retain observed final policy transitions in memory until the durable spool
    // accepts them. A missing replay must not silently discard an audit event.
    private final Map<String, java.util.ArrayDeque<PendingPolicy>> pendingPolicies = new ConcurrentHashMap<>();
    // A watch disconnect may hide a Remove. Do not retry previously queued
    // transitions until usbguard confirms that their devices are still present.
    // All queue mutations and recoveries run under this listener's monitor.
    private boolean pendingPoliciesRequireLiveVerification;
    // Even without a pending policy, watch loss can hide a Remove and leave the
    // previous insertion/cooldown suppressing a genuine rapid reinsertion.
    private boolean observedRequireLiveVerification;
    // Serialize daemon queries independently of the watch parser monitor. A slow
    // usbguard IPC request must never stop the watch reader from consuming Remove.
    private final AtomicBoolean recoveryQueryInFlight = new AtomicBoolean();
    // Any watch input/reset invalidates a device-list snapshot taken before it.
    private long watchStateVersion;
    private final Map<Integer, DeviceObservation> observed = new ConcurrentHashMap<>();
    private final Map<Integer, String> notifiedRuntime = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder sectionLines = new StringBuilder();
    private WatchSection section = WatchSection.NONE;
    private volatile boolean running = true;
    private volatile Process process;

    public enum WatchSection { NONE, PRESENCE_CHANGED, POLICY_CHANGED, POLICY_APPLIED }

    @Autowired
    public UsbGuardEventListener(UsbGuardEventParser parser, ActiveUserResolver users, EndpointManager endpoints,
                                 SystemInfoProvider system, EventSenderService sender,
                                 DesktopNotificationService notifier, UsbGuardGateway gateway) {
        this(parser, users, endpoints, system, sender, notifier, gateway,
                () -> new ProcessBuilder("usbguard", "watch").redirectErrorStream(true).start(),
                TimeUnit.SECONDS.toMillis(5));
    }

    // Existing module tests do not require an actual USBGuard process or daemon.
    public UsbGuardEventListener(UsbGuardEventParser parser, ActiveUserResolver users, EndpointManager endpoints,
                                 SystemInfoProvider system, EventSenderService sender,
                                 DesktopNotificationService notifier) {
        this(parser, users, endpoints, system, sender, notifier, null,
                () -> new ProcessBuilder("usbguard", "watch").redirectErrorStream(true).start(),
                TimeUnit.SECONDS.toMillis(5));
    }

    UsbGuardEventListener(UsbGuardEventParser parser, ActiveUserResolver users, EndpointManager endpoints,
                          SystemInfoProvider system, EventSenderService sender, DesktopNotificationService notifier,
                          WatchProcessLauncher launcher, long delay) {
        this(parser, users, endpoints, system, sender, notifier, null, launcher, delay);
    }

    UsbGuardEventListener(UsbGuardEventParser parser, ActiveUserResolver users, EndpointManager endpoints,
                          SystemInfoProvider system, EventSenderService sender, DesktopNotificationService notifier,
                          UsbGuardGateway gateway, WatchProcessLauncher launcher, long delay) {
        if (launcher == null || delay < 0) throw new IllegalArgumentException("watch process launcher/delay");
        this.eventParser = parser;
        this.presenceParser = new UsbGuardPresenceParser(parser);
        this.activeUserResolver = users;
        this.endpointManager = endpoints;
        this.systemInfoProvider = system;
        this.eventSenderService = sender;
        this.desktopNotificationService = notifier;
        this.gateway = gateway;
        this.watchProcessLauncher = launcher;
        this.reconnectDelayMillis = delay;
    }

    @PostConstruct
    public void startListening() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            startWatchWorker();
        }
    }

    void startWatchWorker() {
        if (running && started.compareAndSet(false, true)) executor.submit(this::watchLoop);
    }

    private void watchLoop() {
        while (running) {
            resetWatchState();
            try {
                process = watchProcessLauncher.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = readWatchLine(reader)) != null) processWatchLine(line);
                }
                if (running) log.warn("USBGuard watch exited; retrying");
            } catch (IOException | RuntimeException error) {
                if (running) log.warn("USBGuard watch IPC failed: {}", error.getMessage());
            } finally {
                // Pending decisions might be lost on a broken watch connection. Query
                // the actual daemon state once before invalidating runtime IDs.
                recoverPending(true);
                Process old = process;
                process = null;
                if (old != null && old.isAlive()) old.destroyForcibly();
                resetWatchState();
            }
            if (!running) break;
            try { TimeUnit.MILLISECONDS.sleep(reconnectDelayMillis); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
    }

    static String readWatchLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int next;
        while ((next = reader.read()) != -1) {
            if (next == '\n') return line.toString();
            if (line.length() >= MAX_WATCH_LINE) throw new IOException("USBGuard watch line exceeds safe limit");
            line.append((char) next);
        }
        return line.isEmpty() ? null : line.toString();
    }

    synchronized void resetWatchState() {
        watchStateVersion++;
        section = WatchSection.NONE;
        sectionLines.setLength(0);
        // A watch restart may hide Remove; all existing observations must be
        // verified against a new daemon snapshot before emitting policy or popup.
        if (gateway != null) {
            if (!observed.isEmpty()) observedRequireLiveVerification = true;
            if (!pendingPolicies.isEmpty()) pendingPoliciesRequireLiveVerification = true;
        }
        // A watch subprocess can reconnect while a USB remains inserted.
        // Preserve last confirmed observations and notification deduplication
        // across reconnects so a later Allow/Revoke is still recognized.
        // A failed IPC query cannot prove a pending insertion was detached.
        // Keep it and revalidate after reconnect before final BLOCK notification.
    }

    int trackedRecentEventCount() { return recent.size(); }
    int trackedPendingPolicyQueueCount() { return pendingPolicies.size(); }

    /** Verify lost final decisions, pending audit transitions and missed Remove events. */
    @Scheduled(fixedDelayString = "${usbshield.agent.pending-event-recheck-ms:3000}")
    public void recoverStalePendingDecisions() { recoverPending(false); }

    void recoverPending(boolean watchLost) {
        final long snapshotVersion;
        final boolean needsQueuedVerification;
        final boolean needsInsertionVerification;
        final long now = System.currentTimeMillis();
        synchronized (this) {
            if (!running) return;
            retryPendingRemovals();
            // A lost watch can hide Remove. Mark all queued transitions unverified
            // before starting an IPC query, and preserve that condition on errors.
            if (watchLost) {
                watchStateVersion++;
                if (!pendingPolicies.isEmpty()) pendingPoliciesRequireLiveVerification = true;
                if (!observed.isEmpty()) observedRequireLiveVerification = true;
            }
            needsQueuedVerification = pendingPoliciesRequireLiveVerification || observedRequireLiveVerification;
            // A pending Insert survives watch loss but must not be finalized
            // from an unverified PolicyApplied replay. Once the daemon snapshot
            // is available, finish it immediately, even when the normal stale
            // pending-insert age threshold has not elapsed. Otherwise an IPC
            // failure followed by a fast successful retry leaves a legitimate
            // BLOCK notification pending until an unrelated later poll.
            needsInsertionVerification = gateway != null && !pendingInserts.isEmpty()
                    && (watchLost || observedRequireLiveVerification
                            || pendingInserts.values().stream().anyMatch(
                                    pending -> now - pending.seenAt() >= RECOVERY_AGE_MILLIS));
            if (!needsQueuedVerification && !needsInsertionVerification) {
                retryQueuedPolicies();
                return;
            }
            if (gateway == null) {
                if (needsQueuedVerification) {
                    log.warn("Cannot verify queued USBGuard policy events without a live gateway; deferring retry");
                }
                return;
            }
            // Do not overlap two IPC queries or allow the older result to win.
            // tryLock semantics also ensure a watch reconnect never queues behind
            // another blocked recovery query.
            if (!recoveryQueryInFlight.compareAndSet(false, true)) return;
            snapshotVersion = watchStateVersion;
        }
        try {
            // IMPORTANT: daemon IPC can block; no synchronized listener monitor
            // is held here. Watch events (especially Remove) keep flowing.
            final List<UsbDeviceInfo> live;
            try { live = gateway.listDevices(); }
            catch (RuntimeException failure) {
                log.warn("Cannot verify USBGuard device state; deferring recovery: {}", failure.getMessage());
                return;
            }
            if (live == null) {
                log.warn("USBGuard device-list query returned no result; deferring recovery");
                return;
            }
            synchronized (this) {
                // An Insert/Remove, PolicyApplied, watch reset or another watch-loss
                // changed our observations during IPC. Never apply an old snapshot.
                if (!running || snapshotVersion != watchStateVersion) return;
                if (needsQueuedVerification) {
                    if (pendingPoliciesRequireLiveVerification) discardDetachedQueuedPolicies(live);
                    if (observedRequireLiveVerification) discardDetachedObservedDevices(live);
                    pendingPoliciesRequireLiveVerification = false;
                    observedRequireLiveVerification = false;
                }
                retryQueuedPolicies();
                // A still-blocked spool write needs another fresh live query on
                // the next recovery attempt; it may be unplugged in the meantime.
                if (needsQueuedVerification && !pendingPolicies.isEmpty()) {
                    pendingPoliciesRequireLiveVerification = true;
                }
                if (!needsInsertionVerification) return;
                for (var entry : List.copyOf(pendingInserts.entrySet())) {
                    PendingInsert pending = entry.getValue();
                    // A fresh IPC snapshot also resolves pending inserts left unverified
                    // by an earlier watch loss. Do not reapply the normal age delay
                    // after that same snapshot has verified the post-reconnect state.
                    if (!watchLost && !needsQueuedVerification
                            && now - pending.seenAt() < RECOVERY_AGE_MILLIS) continue;
                    for (UsbDeviceInfo device : live) {
                        if (device.runtimeId() == entry.getKey()
                                && identity(device).equals(pending.identity())
                                && isTarget(device.state())) {
                            applyFinalDecision(device, device.state(), pending);
                            break;
                        }
                    }
                }
            }
        } finally {
            recoveryQueryInFlight.set(false);
        }
    }

    private void discardDetachedQueuedPolicies(List<UsbDeviceInfo> live) {
        for (var entry : List.copyOf(pendingPolicies.entrySet())) {
            var transitions = entry.getValue();
            if (transitions.isEmpty()) {
                pendingPolicies.remove(entry.getKey(), transitions);
                continue;
            }
            UsbDeviceInfo queued = transitions.getFirst().device();
            boolean attached = live.stream().anyMatch(device ->
                    device.massStorage() && isTarget(device.state())
                            && device.runtimeId() == queued.runtimeId()
                            && identity(device).equals(identity(queued)));
            if (!attached) {
                transitions.clear();
                pendingPolicies.remove(entry.getKey(), transitions);
                // A missed Remove must not leave an observation that suppresses
                // a later physical insertion with the same runtime identity.
                DeviceObservation prior = observed.get(queued.runtimeId());
                if (prior != null && prior.identity().equals(identity(queued))) {
                    forgetDetachedObservation(queued.runtimeId(), prior);
                }
            }
        }
    }

    private void discardDetachedObservedDevices(List<UsbDeviceInfo> live) {
        for (var entry : List.copyOf(observed.entrySet())) {
            int id = entry.getKey();
            DeviceObservation prior = entry.getValue();
            boolean attached = live.stream().anyMatch(device ->
                    device.massStorage() && isTarget(device.state())
                            && device.runtimeId() == id
                            && prior.identity().equals(identity(device)));
            if (!attached) {
                discardQueuedPolicies(id);
                forgetDetachedObservation(id, prior);
            }
        }
    }

    private void forgetDetachedObservation(int id, DeviceObservation prior) {
        if (!observed.remove(id, prior)) return;
        pendingInserts.remove(id);
        pendingFinalAudits.remove(id);
        notifiedRuntime.remove(id);
        // Match the explicit Remove path: reset short event dedup and the
        // notification policy's cooldown for the exact previous device.
        String recentPrefix = prior.identity() + ":" + id + ":";
        recent.keySet().removeIf(key -> key.startsWith(recentPrefix));
        try { desktopNotificationService.onUsbRemoved(prior.user(), prior.device()); }
        catch (RuntimeException failure) {
            log.warn("Notifier cleanup failed after missed USB removal: {}", failure.getMessage());
        }
    }

    public synchronized void processWatchLine(String line) {
        if (line == null) return;
        String value = line.trim();
        if (value.isEmpty() || value.startsWith("[IPC]")) return;
        // Invalidate an IPC snapshot even while a new watch section is only
        // partially parsed; applying its old result could erase a fresh device.
        watchStateVersion++;
        if (value.startsWith("[device] ")) {
            section = value.startsWith("[device] PresenceChanged:") ? WatchSection.PRESENCE_CHANGED
                    : value.startsWith("[device] PolicyChanged:") ? WatchSection.POLICY_CHANGED
                    : value.startsWith("[device] PolicyApplied:") ? WatchSection.POLICY_APPLIED
                    : WatchSection.NONE;
            sectionLines.setLength(0);
        }
        if (section == WatchSection.NONE) return;
        if (value.length() >= MAX_WATCH_LINE || sectionLines.length() > MAX_WATCH_LINE - value.length() - 1) {
            section = WatchSection.NONE;
            sectionLines.setLength(0);
            return;
        }
        sectionLines.append(value).append('\n');
        if (!value.startsWith("device_rule=")) return;
        try {
            if (section == WatchSection.PRESENCE_CHANGED) {
                presenceParser.parse(sectionLines.toString()).ifPresent(this::handlePresence);
            } else if (section == WatchSection.POLICY_APPLIED) {
                String raw = sectionLines.toString();
                var header = POLICY_ID.matcher(raw.lines().findFirst().orElse(""));
                var target = POLICY_TARGET.matcher(raw);
                if (header.matches() && target.find()) {
                    int id = Integer.parseInt(header.group(1));
                    String applied = target.group(1);
                    UsbDeviceInfo parsed = eventParser.parseDeviceLine(value);
                    // Only accept the same ID, class and final target in the applied rule.
                    if (parsed != null && parsed.massStorage() && applied.equals(parsed.state())) {
                        UsbDeviceInfo effective = withState(parsed, id, applied);
                        PendingInsert pending = pendingInserts.get(id);
                        if (pending != null && pending.identity().equals(identity(effective))) {
                            applyFinalDecision(effective, applied, pending);
                        } else {
                            applyLaterPolicy(effective, applied);
                        }
                    }
                }
            }
        } catch (RuntimeException failure) {
            log.error("USBGuard watch section processing failed; continuing listener", failure);
        } finally {
            section = WatchSection.NONE;
            sectionLines.setLength(0);
        }
    }

    private void handlePresence(UsbGuardPresenceEvent event) {
        UsbDeviceInfo device = event.device();
        String type = event.presenceType() == UsbGuardPresenceEvent.PresenceType.INSERT ? "CONNECTED" : "DISCONNECTED";
        // A watch reconnect may replay Insert for a device already observed
        // without a matching Remove. Even if its provisional target differs,
        // it is not proof of a new physical insertion. Only PolicyApplied may
        // establish the new effective decision for that observed device.
        DeviceObservation alreadyPresent = observed.get(device.runtimeId());
        if ("CONNECTED".equals(type) && alreadyPresent != null
                && alreadyPresent.identity().equals(identity(device))) {
            // The first Insert may have reached this observation map before
            // durable enqueue failed (e.g. enrollment not ready). Retry only
            // the original Insert target; never reinterpret a later replayed
            // provisional target as a new CONNECTED event.
            if (!alreadyPresent.insertionQueued()
                    && alreadyPresent.insertionTarget().equals(event.target())
                    && sendAudit(alreadyPresent.insertionAudit())) {
                observed.computeIfPresent(device.runtimeId(), (id, prior) ->
                        prior.identity().equals(identity(device)) ? prior.queued() : prior);
            }
            return;
        }
        // Duplicate watch sections must not recreate pending transitions and popup.
        String recentKey = identity(device) + ":" + device.runtimeId() + ":" + type + ":" + event.target();
        Long previous = recent.get(recentKey);
        long at = System.currentTimeMillis();
        if (previous != null && at >= previous && at - previous < DEDUP_WINDOW_MILLIS) return;
        ActiveUserInfo user = resolveUser();
        if ("CONNECTED".equals(type) && device.massStorage() && device.runtimeId() >= 0) {
            // This runtime ID may have belonged to another device before a missed
            // Remove. Its queued policy transitions must never be sent for this Insert.
            discardQueuedPolicies(device.runtimeId());
            // Remember attribution at insertion, even if the graphical user changes later.
            if (observed.size() >= MAX_RECENT_EVENTS && !observed.containsKey(device.runtimeId())) {
                observed.clear(); pendingInserts.clear(); pendingFinalAudits.clear();
                log.warn("USBGuard device tracking bound reached; reset transient states");
            }
            observed.put(device.runtimeId(), new DeviceObservation(
                    identity(device), event.target(), user, event.target(), false, device,
                    PendingAudit.capture(device, type, event.target(), user)));
            pendingFinalAudits.remove(device.runtimeId());
            pendingInserts.put(device.runtimeId(), new PendingInsert(identity(device), event.target(), user,
                    System.currentTimeMillis()));
        } else if ("DISCONNECTED".equals(type)) {
            // Unsent transitions no longer describe an attached device. Drop them
            // before recovery can enqueue stale POLICY_APPLIED audit events.
            discardQueuedPolicies(device.runtimeId());
            DeviceObservation prior = observed.remove(device.runtimeId());
            pendingInserts.remove(device.runtimeId());
            pendingFinalAudits.remove(device.runtimeId());
            notifiedRuntime.remove(device.runtimeId());
            // A physical Remove ends the old connection lifecycle regardless
            // of whether DISCONNECTED can be spooled. Do not let stale recent
            // CONNECTED suppress a rapid reinsertion with the reused runtime ID.
            String oldPrefix = identity(device) + ":" + device.runtimeId() + ":";
            recent.keySet().removeIf(key -> key.startsWith(oldPrefix));
            if (prior != null && prior.identity().equals(identity(device))) user = prior.user();
            try { desktopNotificationService.onUsbRemoved(user, device); }
            catch (RuntimeException failure) { log.warn("Notifier cleanup failed: {}", failure.getMessage()); }
        }
        if ("DISCONNECTED".equals(type)) {
            PendingAudit removal = PendingAudit.capture(device, type, event.target(), user);
            if (!sendAudit(removal)) {
                if (pendingRemovals.size() >= MAX_RECENT_EVENTS) {
                    log.error("Pending USB removal audit capacity exhausted; durable audit unavailable");
                } else {
                    pendingRemovals.addLast(removal);
                }
            }
        } else if ("CONNECTED".equals(type) && device.massStorage() && device.runtimeId() >= 0) {
            DeviceObservation inserted = observed.get(device.runtimeId());
            if (inserted != null && inserted.identity().equals(identity(device))
                    && sendAudit(inserted.insertionAudit())) {
                observed.computeIfPresent(device.runtimeId(), (id, prior) ->
                        prior.identity().equals(identity(device)) ? prior.queued() : prior);
            }
        } else {
            sendAudit(PendingAudit.capture(device, type, event.target(), user));
        }
    }

    private void applyFinalDecision(UsbDeviceInfo device, String target, PendingInsert pending) {
        DeviceObservation initial = observed.get(device.runtimeId());
        if (initial == null || !initial.identity().equals(pending.identity())) return;
        // Capture a watch-delivered final transition now, but never emit it
        // after a watch loss until a fresh IPC snapshot confirms attachment.
        if (!target.equals(pending.initialTarget())) {
            pendingFinalAudits.compute(device.runtimeId(), (id, prior) ->
                    prior != null && prior.target().equals(target) && identity(prior.device()).equals(pending.identity())
                            ? prior : PendingAudit.capture(device, "POLICY_APPLIED", target, pending.user()));
        }
        if (observedRequireLiveVerification) return;
        // The first CONNECTED enqueue might have failed (no enrollment, full disk).
        // Never discard the pending final decision until the original event is durable.
        if (!initial.insertionQueued()) {
            if (!sendAudit(initial.insertionAudit())) return;
            observed.computeIfPresent(device.runtimeId(), (id, prior) ->
                    prior.identity().equals(pending.identity()) ? prior.queued() : prior);
        }
        if (!target.equals(pending.initialTarget())) {
            PendingAudit finalAudit = pendingFinalAudits.compute(device.runtimeId(), (id, prior) ->
                    prior != null && prior.target().equals(target) && identity(prior.device()).equals(pending.identity())
                            ? prior : PendingAudit.capture(device, "POLICY_APPLIED", target, pending.user()));
            if (!sendAudit(finalAudit)) return;
        }
        if (!pendingInserts.remove(device.runtimeId(), pending)) return;
        pendingFinalAudits.remove(device.runtimeId());
        observed.computeIfPresent(device.runtimeId(), (id, prior) ->
                prior.identity().equals(pending.identity()) ? prior.withTarget(target) : prior);
        if ("allow".equals(target)) notifiedRuntime.remove(device.runtimeId());
        if ("block".equals(target) || "reject".equals(target)) notifyBlocked(pending.user(), device);
    }

    private void applyLaterPolicy(UsbDeviceInfo device, String target) {
        DeviceObservation prior = observed.get(device.runtimeId());
        // Do not invent audit events after an orphan PolicyApplied watch replay.
        if (prior == null || !prior.identity().equals(identity(device))) return;
        String key = device.runtimeId() + ":" + identity(device);
        var transitions = pendingPolicies.get(key);
        String last = transitions == null || transitions.isEmpty()
                ? prior.target() : transitions.getLast().target();
        if (!target.equals(last)) {
            // Free locally proven stale/empty queues before rejecting a new
            // identity at the global bound. Do not discard an attached device's
            // pending audit merely to make space.
            if (transitions == null && pendingPolicies.size() >= MAX_RECENT_EVENTS) {
                pruneUnobservedPendingPolicies();
            }
            if ((transitions == null && pendingPolicies.size() >= MAX_RECENT_EVENTS)
                    || (transitions != null && transitions.size() >= 32)) {
                log.error("USBGuard pending policy audit queue bound reached; no event can be acknowledged");
                return;
            }
            if (transitions == null) {
                transitions = new java.util.ArrayDeque<>();
                pendingPolicies.put(key, transitions);
            }
            transitions.addLast(new PendingPolicy(device, target, prior.user(),
                    PendingAudit.capture(device, "POLICY_APPLIED", target, prior.user())));
        }
        retryQueuedPolicies();
    }

    private void pruneUnobservedPendingPolicies() {
        for (var entry : List.copyOf(pendingPolicies.entrySet())) {
            var queue = entry.getValue();
            if (queue.isEmpty()) {
                pendingPolicies.remove(entry.getKey(), queue);
                continue;
            }
            UsbDeviceInfo queued = queue.getFirst().device();
            DeviceObservation current = observed.get(queued.runtimeId());
            if (current == null || !current.identity().equals(identity(queued))) {
                queue.clear();
                pendingPolicies.remove(entry.getKey(), queue);
            }
        }
        if (pendingPolicies.isEmpty()) pendingPoliciesRequireLiveVerification = false;
    }

    private void discardQueuedPolicies(int runtimeId) {
        // Runtime IDs are reused by USBGuard. Do not let the old device's audit
        // queue survive a Remove or be attributed to a replacement device.
        pendingPolicies.keySet().removeIf(key -> key.startsWith(runtimeId + ":"));
        if (pendingPolicies.isEmpty()) pendingPoliciesRequireLiveVerification = false;
    }

    private void retryQueuedPolicies() {
        // Even a fresh PolicyApplied watch section must not flush an older
        // unverified FIFO after the watch connection has been interrupted.
        if (pendingPoliciesRequireLiveVerification || observedRequireLiveVerification) return;
        for (var entry : List.copyOf(pendingPolicies.entrySet())) {
            var transitions = entry.getValue();
            while (!transitions.isEmpty()) {
                PendingPolicy next = transitions.getFirst();
                UsbDeviceInfo device = next.device();
                DeviceObservation prior = observed.get(device.runtimeId());
                if (prior == null || !prior.identity().equals(identity(device))) {
                    // Removal, lost observation, or reused runtime ID: never
                    // emit a stale transition, even after a failed enqueue.
                    transitions.clear();
                    break;
                }
                if (!sendAudit(next.audit())) break;
                transitions.removeFirst();
                observed.put(device.runtimeId(), prior.withTarget(next.target()));
                if ("allow".equals(next.target())) notifiedRuntime.remove(device.runtimeId());
                if ("block".equals(next.target()) || "reject".equals(next.target())) {
                    notifyBlocked(next.user(), device);
                }
            }
            if (transitions.isEmpty()) pendingPolicies.remove(entry.getKey(), transitions);
        }
    }

    private void retryPendingRemovals() {
        while (!pendingRemovals.isEmpty()) {
            if (!sendAudit(pendingRemovals.getFirst())) break;
            pendingRemovals.removeFirst();
        }
    }

    private void notifyBlocked(ActiveUserInfo user, UsbDeviceInfo device) {
        if (!device.massStorage() || user == null || !"RESOLVED".equals(user.status())) return;
        if (identity(device).equals(notifiedRuntime.get(device.runtimeId()))) return;
        try {
            desktopNotificationService.notifyBlockedUsb(user, device);
            notifiedRuntime.put(device.runtimeId(), identity(device));
        } catch (RuntimeException error) {
            log.warn("Notification failed independently of USB enforcement: {}", error.getMessage());
        }
    }

    private ActiveUserInfo resolveUser() {
        try {
            ActiveUserInfo resolved = activeUserResolver.resolveCurrentActiveUser();
            return resolved == null ? ActiveUserInfo.unknown("resolver-null") : resolved;
        } catch (RuntimeException error) { return ActiveUserInfo.unknown("resolver-failure"); }
    }

    private static boolean isTarget(String target) {
        return "block".equals(target) || "reject".equals(target) || "allow".equals(target);
    }

    private static String identity(UsbDeviceInfo device) {
        return device.hash() != null && !device.hash().isBlank() ? device.hash()
                : device.vendorId() + ":" + device.productId() + ":" + device.serial();
    }

    private static UsbDeviceInfo withState(UsbDeviceInfo device, int id, String target) {
        return new UsbDeviceInfo(id, target, device.vendorId(), device.productId(), device.serial(),
                device.name(), device.hash(), device.interfaces(), device.massStorage(), device.rawRule());
    }

    /** Returns true only after the event was successfully queued, or already queued. */
    private boolean sendAudit(PendingAudit audit) {
        UsbDeviceInfo device = audit.device();
        String eventType = audit.eventType();
        String target = audit.target();
        ActiveUserInfo user = audit.user();
        if (!isTarget(target)) return false;
        String id = identity(device);
        String key = id + ":" + device.runtimeId() + ":" + eventType + ":" + target;
        long now = System.currentTimeMillis();
        Long old = recent.get(key);
        // POLICY_APPLIED transitions have their own FIFO/dedup state. The same
        // final target can legitimately recur after an intervening transition.
        if (!"POLICY_APPLIED".equals(eventType)
                && old != null && now >= old && now - old < DEDUP_WINDOW_MILLIS) return true;
        // Build the payload inside the failure boundary too: enrollment may not
        // be ready yet, and a rejected event must remain retryable.
        try {
            UsbEventPayload payload = UsbEventPayload.builder()
                    .eventId(audit.eventId())
                    .endpointId(endpointManager.getEndpointId())
                    .hostname(systemInfoProvider.getHostname())
                    .linuxUsername(user.username()).linuxUid(user.uid())
                    .sessionId(user.sessionId()).sessionType(user.sessionType()).seat(user.seat())
                    .device(device.toContractDeviceMap()).eventType(eventType)
                    .decision("allow".equals(target) ? "ALLOWED" : "BLOCKED")
                    .occurredAt(audit.occurredAt()).build();
            eventSenderService.enqueuePayload(payload);
            // Removal-side dedup was already reset synchronously at physical
            // Remove. A delayed DISCONNECTED retry must NOT clear the cooldown
            // of a newer insertion that reused the same runtime ID.
            if ("CONNECTED".equals(eventType)) {
                recent.keySet().removeIf(k -> k.startsWith(id + ":" + device.runtimeId() + ":")
                        && k.contains(":DISCONNECTED:"));
            }
            if (recent.size() >= MAX_RECENT_EVENTS && !recent.containsKey(key)) {
                recent.entrySet().removeIf(entry -> now < entry.getValue() || now - entry.getValue() > DEDUP_WINDOW_MILLIS);
                if (recent.size() >= MAX_RECENT_EVENTS) recent.entrySet().stream()
                        .min(Comparator.comparingLong(Map.Entry::getValue))
                        .ifPresent(entry -> recent.remove(entry.getKey(), entry.getValue()));
            }
            recent.put(key, now);
            return true;
        } catch (RuntimeException error) {
            log.error("USB event durable queue failed; USBGuard remains independent", error);
            return false;
        }
    }

    /** Legacy integration entrypoint: explicitly supplied rule, not the live watch path. */
    public synchronized void handleDeviceRuleEvent(String raw, String eventType) {
        watchStateVersion++;
        UsbDeviceInfo parsed = eventParser.parseDeviceLine(raw);
        if (parsed == null || !("CONNECTED".equals(eventType) || "DISCONNECTED".equals(eventType))) return;
        UsbGuardPresenceEvent event = new UsbGuardPresenceEvent(
                "CONNECTED".equals(eventType) ? UsbGuardPresenceEvent.PresenceType.INSERT :
                        UsbGuardPresenceEvent.PresenceType.REMOVE, parsed.state(), parsed);
        handlePresence(event);
        // In direct test/legacy mode, the caller explicitly supplied the effective state.
        if ("CONNECTED".equals(eventType) && parsed.massStorage() &&
                ("block".equals(parsed.state()) || "reject".equals(parsed.state()))) {
            notifyBlocked(resolveUser(), parsed);
        }
    }

    @PreDestroy
    public synchronized void stopListening() {
        watchStateVersion++;
        running = false;
        Process live = process;
        if (live != null && live.isAlive()) live.destroy();
        executor.shutdownNow();
        resetWatchState();
        observed.clear();
        pendingPolicies.clear();
        pendingFinalAudits.clear();
        pendingRemovals.clear();
        pendingInserts.clear();
        pendingPoliciesRequireLiveVerification = false;
        observedRequireLiveVerification = false;
        notifiedRuntime.clear();
        recent.clear();
    }
}
