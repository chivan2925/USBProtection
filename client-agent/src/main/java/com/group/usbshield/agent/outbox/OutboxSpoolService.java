package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class OutboxSpoolService {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private Path spoolPath;
    // Do not hold the spool write monitor while waiting on Admin Server HTTP.
    private final Object drainLock = new Object();
    // A failed directory sync after a quarantine rename makes its durability
    // uncertain. Do not drain later events until both directory entries have
    // been verified or the move has been durably rolled back.
    private volatile boolean quarantineSyncUncertain;
    private static final Pattern EVENT_FILENAME = Pattern.compile("event_(\\d{13,19})_.*\\.json");
    private long lastLogicalTimestamp;
    // Single-writer Agent spool: cache accounting after a startup scan. A directory
    // modification (drain, quarantine or an operator-created entry) triggers a
    // rescan. A failed publish invalidates the cache so uncertain evidence is
    // counted before any further write. The root/service account owns this path.
    private SpoolUsage cachedUsage;
    private FileTime cachedSpoolMtime;
    private FileTime cachedQuarantineMtime;
    private long usageInspections;

    public OutboxSpoolService(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (properties.getDataDir() == null || properties.getDataDir().isBlank()) {
            spoolPath = null;
            log.error("Outbox dataDir is not configured; refusing audit storage");
            return;
        }
        Path configuredPath = Path.of(properties.getDataDir(), "spool");
        if (properties.isRequireAbsoluteStorage() && !configuredPath.isAbsolute()) {
            spoolPath = null;
            log.error("Live Agent outbox dataDir must be absolute; refusing audit storage");
            return;
        }
        try {
            if (!Files.exists(configuredPath)) {
                Files.createDirectories(configuredPath);
            }
            this.spoolPath = configuredPath;
        } catch (Exception e) {
            // Fail closed: do not redirect audit evidence to an untrusted relative path.
            this.spoolPath = null;
            log.error("Cannot initialize configured outbox directory {}; audit spool disabled: {}",
                    configuredPath, e.getMessage());
        }
        if (spoolPath != null) {
            // Re-synchronize recovered evidence directories before serving new
            // events on Linux. If a past quarantine move was not durable,
            // startup must fail closed rather than silently drain later audit.
            try {
                syncDirectory(spoolPath);
                Path quarantine = spoolPath.resolve("quarantine");
                if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isDirectory(quarantine, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Quarantine path is not a directory");
                    }
                    syncDirectory(quarantine);
                }
            } catch (IOException error) {
                log.error("Cannot verify outbox directory durability during startup: {}", error.getMessage());
                spoolPath = null;
                return;
            }
            // Preserve the FIFO key across restarts while there are old events.
            // Within one process this logical clock also resolves same-ms writes.
            try (Stream<Path> entries = Files.list(spoolPath)) {
                lastLogicalTimestamp = entries
                        .map(p -> EVENT_FILENAME.matcher(p.getFileName().toString()))
                        .filter(Matcher::matches)
                        .mapToLong(m -> {
                            try { return Long.parseLong(m.group(1)); }
                            catch (NumberFormatException invalid) { return 0L; }
                        }).max().orElse(0L);
            } catch (IOException error) {
                log.warn("Cannot inspect existing spool order; rejecting new writes until repaired: {}", error.getMessage());
                spoolPath = null;
            }
            if (spoolPath != null) {
                try { currentUsage(); }
                catch (IOException | ArithmeticException error) {
                    log.error("Cannot account for existing outbox evidence; refusing new writes: {}", error.getMessage());
                    spoolPath = null;
                }
            }
        }
        log.info("Outbox Spool directory initialized at: {}", spoolPath);
    }

    /**
     * Lưu tạm sự kiện vào file đĩa khi gửi lên Admin Server thất bại
     * @return true nếu ghi file thành công, false nếu thất bại
     */
    public synchronized boolean spool(UsbEventPayload event) {
        if (spoolPath == null || event == null) {
            log.error("Spool directory or payload is unavailable; USB event could not be persisted");
            return false;
        }
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        Path temporary = null;
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(event);
            // Include quarantined evidence in the storage budget; operator review
            // is required before deleting anything. Never silently evict audit.
            SpoolUsage usage = currentUsage();
            long maxBytes = properties.getOutboxMaxBytes();
            int maxEvents = properties.getOutboxMaxEvents();
            if (maxBytes <= 0 || maxEvents <= 0 || json.length > maxBytes
                    || usage.bytes() > maxBytes - json.length || usage.eventFiles() >= maxEvents) {
                log.error("[OUTBOX-CAPACITY] Queue/evidence capacity reached (events={}, bytes={}); "
                                + "audit enqueue rejected, USBGuard enforcement remains independent. "
                                + "Operator must restore disk capacity or increase configured limits.",
                        usage.eventFiles(), usage.bytes());
                return false;
            }
            // A logical millisecond key, strictly greater than the prior write,
            // is sortable with legacy event_<epochMs>_<uuid>.json files. It avoids
            // same-millisecond UUID ordering and handles clock rollback.
            long nextTimestamp = Math.max(System.currentTimeMillis(), Math.addExact(lastLogicalTimestamp, 1L));
            String fileName = String.format("event_%013d_%s.json", nextTimestamp, UUID.randomUUID());
            Path target = spoolPath.resolve(fileName);
            temporary = spoolPath.resolve("." + fileName + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(json);
                output.getFD().sync();
            }
            movePublished(temporary, target);
            lastLogicalTimestamp = nextTimestamp;
            // FileOutputStream.fd.sync only persists file contents. On Linux,
            // publishing a new filename is not crash-durable until the parent
            // directory entry is synced too. On failure report non-durability;
            // never delete the published evidence file.
            syncDirectory(spoolPath);
            // Same file was atomically renamed from .tmp to .json. Exactly one
            // new event and json.length bytes have been published durably.
            cachedUsage = new SpoolUsage(Math.addExact(usage.bytes(), json.length),
                    Math.addExact(usage.eventFiles(), 1L));
            captureDirectoryMtimes();
            log.warn("[OUTBOX-SPOOL] Admin Server unreachable. Event saved to disk: {}", fileName);
            return true;
        } catch (Exception error) {
            // A failed fsync/rename may still leave evidence on disk; force a
            // complete accounting scan on the next enqueue, never assume absence.
            cachedUsage = null;
            log.error("Failed to persist USB event: {}", error.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException failed) {
                    cachedUsage = null;
                    log.warn("Failed to clean up partial spool write");
                }
            }
        }
    }

    private record SpoolUsage(long bytes, long eventFiles) {}

    // Only for regression tests: a normal enqueue should not walk the backlog.
    long usageInspectionCount() { return usageInspections; }

    private static FileTime directoryMtime(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid outbox evidence directory: " + directory);
        return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS);
    }

    private void captureDirectoryMtimes() throws IOException {
        cachedSpoolMtime = directoryMtime(spoolPath);
        cachedQuarantineMtime = directoryMtime(spoolPath.resolve("quarantine"));
    }

    private SpoolUsage currentUsage() throws IOException {
        // O(1) metadata reads for ordinary appends; a metadata change indicates
        // external modification, drain deletion or quarantine relocation.
        FileTime active = directoryMtime(spoolPath);
        FileTime isolated = directoryMtime(spoolPath.resolve("quarantine"));
        if (cachedUsage == null || !Objects.equals(active, cachedSpoolMtime)
                || !Objects.equals(isolated, cachedQuarantineMtime)) {
            // Invalidate first. A failed scan must not leave a trusted stale total.
            cachedUsage = null;
            SpoolUsage actual = inspectUsage();
            captureDirectoryMtimes();
            cachedUsage = actual;
        }
        return cachedUsage;
    }

    private SpoolUsage inspectUsage() throws IOException {
        usageInspections++;
        long bytes = 0;
        long eventFiles = 0;
        // Bounded depth: direct event_*.json plus the quarantine directory.
        try (Stream<Path> paths = Files.walk(spoolPath, 2)) {
            for (java.util.Iterator<Path> entries = paths.iterator(); entries.hasNext();) {
                Path path = entries.next();
                if (Files.isSymbolicLink(path)) throw new IOException("Symlink found in protected spool");
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                bytes = Math.addExact(bytes, Files.size(path));
                String name = path.getFileName().toString();
                if (name.endsWith(".json") || name.endsWith(".tmp")) eventFiles++;
            }
        }
        return new SpoolUsage(bytes, eventFiles);
    }

    private static void movePublished(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException notSupported) { Files.move(source, target); }
    }

    protected void syncDirectory(Path directory) throws IOException {
        // Linux deployment guarantee: directory fsync required after publish.
        // Windows cannot reliably open a directory with FileChannel; its native
        // crash semantics require separate validation and are not claimed here.
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    /**
     * Preserve rejected/corrupted source bytes for an operator instead of
     * losing evidence or letting one foreign identity block all current events.
     * A failed move leaves the source in the active spool and stops draining.
     */
    private boolean quarantine(File file, String reason) {
        Path source = file.toPath();
        Path directory = spoolPath.resolve("quarantine");
        try {
            if (Files.isSymbolicLink(directory)) throw new IOException("Quarantine directory is a symlink");
            Files.createDirectories(directory);
            try { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
            catch (UnsupportedOperationException windows) { /* Windows ACLs are tested separately. */ }
            // The quarantine directory itself must survive a crash before any
            // event can be moved into it (important when first created).
            syncDirectory(spoolPath);
            String name = file.getName();
            Path destination = directory.resolve(name);
            if (Files.exists(destination)) {
                destination = directory.resolve(UUID.randomUUID() + "_" + name);
            }
            movePublished(source, destination);
            try {
                syncDirectory(spoolPath);
                syncDirectory(directory);
            } catch (IOException | SecurityException syncFailure) {
                // The rename happened, but durability was NOT confirmed.
                // Return the file to the active FIFO where possible, and do
                // not advance to newer events in this drain pass.
                try {
                    movePublished(destination, source);
                    syncDirectory(spoolPath);
                    syncDirectory(directory);
                    log.error("[OUTBOX-QUARANTINE] Directory sync failed for {}; "
                            + "restored original audit to active FIFO", name);
                } catch (IOException | SecurityException rollbackFailure) {
                    quarantineSyncUncertain = true;
                    syncFailure.addSuppressed(rollbackFailure);
                    log.error("[OUTBOX-QUARANTINE] Failed to durably restore {}; "
                            + "blocking further drain until both directories can be synced", name);
                }
                throw syncFailure;
            }
            try { Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException windows) { /* Unsupported on Windows. */ }
            catch (IOException permissions) {
                log.warn("Quarantined evidence file mode could not be hardened; verify private directory ACL");
            }
            log.error("[OUTBOX-QUARANTINE] Preserved {} for operator review ({})", name, reason);
            return true;
        } catch (IOException | SecurityException error) {
            log.error("[OUTBOX-QUARANTINE] Cannot isolate {} ({}); pausing drain: {}",
                    file.getName(), reason, error.getMessage());
            return false;
        }
    }

    /**
     * Quét và gửi bù các sự kiện cũ trong hàng đợi khi Server đã ONLINE trở lại
     */
    public void drainOutbox(String endpointId, String agentToken, AdminServerClient client) {
        synchronized (drainLock) {
        if (spoolPath == null || !Files.exists(spoolPath)) {
            return;
        }
        if (quarantineSyncUncertain) {
            try {
                syncDirectory(spoolPath);
                syncDirectory(spoolPath.resolve("quarantine"));
                quarantineSyncUncertain = false;
            } catch (IOException | SecurityException failure) {
                log.error("[OUTBOX-QUARANTINE] Directory durability still uncertain; "
                        + "refusing to drain newer audit: {}", failure.getMessage());
                return;
            }
        }

        File[] files = spoolPath.toFile().listFiles((dir, name) -> name.startsWith("event_") && name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        // Sắp xếp theo thứ tự thời gian xảy ra sự kiện (FIFO)
        Arrays.sort(files, Comparator.comparing(File::getName));
        log.info("[OUTBOX-DRAIN] Found {} spooled event(s). Attempting to deliver in FIFO order...", files.length);

        for (File file : files) {
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                // Do not read or move a symlink using privileged service rights.
                log.error("[OUTBOX-SPOOL] Non-regular event entry; operator inspection required: {}", file.getName());
                break;
            }
            UsbEventPayload payload;
            try {
                payload = objectMapper.readValue(file, UsbEventPayload.class);
            } catch (Exception e) {
                // Never delete malformed evidence. Isolate it and continue with
                // valid events; stop instead if the quarantine move fails.
                if (!quarantine(file, "unparseable JSON")) break;
                continue;
            }

            // Older spool files predate the eventId protocol. Derive a stable ID from
            // the persistent filename so retries after restart deduplicate server-side.
            if (payload.getEventId() == null || payload.getEventId().isBlank()) {
                payload.setEventId(UUID.nameUUIDFromBytes(
                        ("usbshield-legacy:" + file.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            }
            // Events captured before enrollment have a temporary local ID.
            // Bind *only* those placeholders to the newly enrolled endpoint;
            // never silently forward an event belonging to a different real
            // endpoint identity (e.g. credentials changed after reinstall).
            String storedId = payload.getEndpointId();
            if (!endpointId.equals(storedId)) {
                if ("UNENROLLED".equals(storedId)
                        || (storedId != null && storedId.startsWith("POC-UNENROLLED-"))) {
                    payload.setEndpointId(endpointId);
                } else {
                    if (!quarantine(file, "foreign endpoint identity")) break;
                    continue;
                }
            }

            boolean success = false;
            try {
                success = client.sendEvent(endpointId, agentToken, payload);
            } catch (Exception e) {
                // Ngoại lệ mạng / HTTP server lỗi -> KHÔNG XÓA FILE, bảo lưu trên đĩa cho chu kỳ kế tiếp
                log.warn("[OUTBOX-DRAIN] Exception while delivering spooled event {}: {}. Preserving event on disk for retry.",
                        file.getName(), e.getMessage());
                break;
            }

            if (success) {
                if (file.delete()) {
                    log.info("[OUTBOX-DRAIN] Successfully delivered and cleared spooled event: {}", file.getName());
                } else {
                    log.warn("[OUTBOX-DRAIN] Successfully delivered but failed to delete file: {}", file.getName());
                }
            } else if (client.lastEventWasPermanentlyRejected()) {
                // Keep exact original bytes for audit. A deterministic HTTP 400/409/413/422
                // must not indefinitely block later valid FIFO events.
                if (!quarantine(file, "permanent Admin HTTP 400/409/413/422 event rejection")) break;
                log.error("[OUTBOX-DRAIN] Permanently rejected event quarantined; advancing to newer audit");
            } else {
                // Auth, network, rate limit, 5xx: retain head and retry FIFO later.
                log.warn("[OUTBOX-DRAIN] Transient Server rejection/unreachable for event {}. Pausing drain.", file.getName());
                break;
            }
        }
        } // drainLock
    }
}
