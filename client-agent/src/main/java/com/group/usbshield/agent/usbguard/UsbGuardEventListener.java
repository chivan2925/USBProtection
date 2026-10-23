package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.ClientUsbEvent;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventEnricher;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class UsbGuardEventListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsbGuardEventListener.class);
    private final UsbGuardEventParser eventParser;
    private final ActiveUserResolver activeUserResolver;
    private final EndpointManager endpointManager;
    private final SystemInfoProvider systemInfoProvider;
    private final EventSenderService eventSenderService;
    private final DesktopNotificationService desktopNotificationService;
    private final UsbGuardPresenceParser presenceParser;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder sectionLines = new StringBuilder();
    private WatchSection section = WatchSection.NONE;
    private volatile boolean running = true;
    private volatile Process process;

    public enum WatchSection {
        NONE, PRESENCE_CHANGED, POLICY_CHANGED, POLICY_APPLIED
    }

    public UsbGuardEventListener(UsbGuardEventParser eventParser, ActiveUserResolver activeUserResolver,
                                 EndpointManager endpointManager, SystemInfoProvider systemInfoProvider,
                                 EventSenderService eventSenderService,
                                 DesktopNotificationService desktopNotificationService) {
        this.eventParser = eventParser;
        this.presenceParser = new UsbGuardPresenceParser(eventParser);
        this.activeUserResolver = activeUserResolver;
        this.endpointManager = endpointManager;
        this.systemInfoProvider = systemInfoProvider;
        this.eventSenderService = eventSenderService;
        this.desktopNotificationService = desktopNotificationService;
    }

    @PostConstruct
    public void startListening() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            executor.submit(this::watchLoop);
        }
    }

    private void watchLoop() {
        while (running) {
            resetWatchState();
            try {
                process = new ProcessBuilder("usbguard", "watch").redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        processWatchLine(line);
                    }
                }
                if (running) {
                    log.warn("USBGuard watch exited; retrying");
                }
            } catch (IOException error) {
                if (running) {
                    log.warn("USBGuard watch IPC failed: {}", error.getMessage());
                }
            } finally {
                Process old = process;
                process = null;
                if (old != null && old.isAlive()) {
                    old.destroyForcibly();
                }
                resetWatchState();
            }
            if (!running) {
                break;
            }
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    synchronized void resetWatchState() {
        section = WatchSection.NONE;
        sectionLines.setLength(0);
        recent.clear();
    }

    public synchronized void processWatchLine(String line) {
        if (line == null) {
            return;
        }
        String value = line.trim();
        if (value.isEmpty() || value.startsWith("[IPC]")) {
            return;
        }
        if (value.startsWith("[device] ")) {
            section = value.startsWith("[device] PresenceChanged:") ? WatchSection.PRESENCE_CHANGED
                    : value.startsWith("[device] PolicyChanged:") ? WatchSection.POLICY_CHANGED
                    : value.startsWith("[device] PolicyApplied:") ? WatchSection.POLICY_APPLIED
                    : WatchSection.NONE;
            sectionLines.setLength(0);
        }
        if (section == WatchSection.NONE) {
            return;
        }
        if (sectionLines.length() + value.length() > 32768) {
            section = WatchSection.NONE;
            sectionLines.setLength(0);
            return;
        }
        sectionLines.append(value).append('\n');
        if (value.startsWith("device_rule=") && section == WatchSection.PRESENCE_CHANGED) {
            presenceParser.parse(sectionLines.toString()).ifPresent(event -> {
                String type = event.presenceType() == UsbGuardPresenceEvent.PresenceType.INSERT
                        ? "CONNECTED" : "DISCONNECTED";
                handle(event.device(), type, event.target());
            });
            section = WatchSection.NONE;
            sectionLines.setLength(0);
        }
    }

    public void handleDeviceRuleEvent(String raw, String eventType) {
        UsbDeviceInfo device = eventParser.parseDeviceLine(raw);
        if (device != null && ("CONNECTED".equals(eventType) || "DISCONNECTED".equals(eventType))) {
            handle(device, eventType, device.state());
        }
    }

    private synchronized void handle(UsbDeviceInfo device, String type, String target) {
        String hash = device.hash();
        String identity = hash != null && !hash.isBlank()
                ? hash : device.vendorId() + ":" + device.productId() + ":" + device.serial();
        String base = identity + ":" + device.runtimeId();
        String key = base + ":" + type;
        String opposite = base + ("CONNECTED".equals(type) ? ":DISCONNECTED" : ":CONNECTED");
        long now = System.currentTimeMillis();
        Long previous = recent.get(key);
        if (previous != null && now >= previous && now - previous < 1500) {
            return;
        }
        recent.remove(opposite);
        recent.put(key, now);
        if (recent.size() > 1024) {
            recent.entrySet().removeIf(entry -> now - entry.getValue() > 3000);
        }

        UsbGuardPresenceEvent event = new UsbGuardPresenceEvent(
                "CONNECTED".equals(type) ? UsbGuardPresenceEvent.PresenceType.INSERT
                        : UsbGuardPresenceEvent.PresenceType.REMOVE, target, device);
        ClientUsbEvent enriched = new UsbEventEnricher(activeUserResolver, Clock.systemUTC())
                .enrich(endpointManager.getEndpointId(), systemInfoProvider.getHostname(), event);
        UsbEventPayload wire = UsbEventPayload.builder()
                .endpointId(enriched.endpointId()).hostname(enriched.hostname())
                .linuxUsername(enriched.linuxUsername()).linuxUid(enriched.linuxUid())
                .sessionId(enriched.sessionId()).sessionType(enriched.sessionType()).seat(enriched.seat())
                .device(enriched.device()).eventType(enriched.eventType()).decision(enriched.decision())
                .occurredAt(enriched.occurredAt()).build();
        ActiveUserInfo user = new ActiveUserInfo(enriched.linuxUsername(), enriched.linuxUid(),
                enriched.sessionId(), enriched.sessionType(), enriched.seat(),
                enriched.linuxUid() == null ? "UNKNOWN" : "RESOLVED", null);
        try {
            if (event.presenceType() == UsbGuardPresenceEvent.PresenceType.REMOVE) {
                desktopNotificationService.onUsbRemoved(user, device);
            } else if (device.massStorage()
                    && ("block".equalsIgnoreCase(target) || "reject".equalsIgnoreCase(target))
                    && "RESOLVED".equals(user.status())) {
                desktopNotificationService.notifyBlockedUsb(user, device);
            }
        } catch (RuntimeException error) {
            log.warn("Notification failed independently of event transport: {}", error.getMessage());
        }
        try {
            eventSenderService.sendPayload(wire);
        } catch (RuntimeException error) {
            log.error("Event transport failed: {}", error.getMessage());
        }
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        Process live = process;
        if (live != null && live.isAlive()) {
            live.destroy();
        }
        executor.shutdownNow();
        resetWatchState();
    }
}
