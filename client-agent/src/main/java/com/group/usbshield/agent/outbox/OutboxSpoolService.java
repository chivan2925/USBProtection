package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
public class OutboxSpoolService {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private Path spoolPath;
    // Do not hold the spool write monitor while waiting on Admin Server HTTP.
    private final Object drainLock = new Object();

    public OutboxSpoolService(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        Path configuredPath = Path.of(properties.getDataDir(), "spool");
        try {
            if (!Files.exists(configuredPath)) {
                Files.createDirectories(configuredPath);
            }
            this.spoolPath = configuredPath;
        } catch (Exception e) {
            Path fallbackPath = Path.of("./data/spool");
            try {
                Files.createDirectories(fallbackPath);
                this.spoolPath = fallbackPath;
            } catch (Exception ex) {
                log.error("Could not initialize outbox spool directory: {}", ex.getMessage());
            }
        }
        log.info("Outbox Spool directory initialized at: {}", spoolPath);
    }

    /**
     * Lưu tạm sự kiện vào file đĩa khi gửi lên Admin Server thất bại
     * @return true nếu ghi file thành công, false nếu thất bại
     */
    public synchronized boolean spool(UsbEventPayload event) {
        if (spoolPath == null) {
            log.error("Spool directory is unavailable; USB event could not be persisted");
            return false;
        }

        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        String fileName = String.format("event_%d_%s.json", System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
        Path target = spoolPath.resolve(fileName);
        Path temporary = spoolPath.resolve("." + fileName + ".tmp");

        try {
            // Write and sync a temporary file, then publish it atomically.
            // A process crash must not leave a partial event_*.json which the
            // recovery loop would misclassify as a corrupted queued event.
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(event);
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(json);
                output.getFD().sync();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            log.warn("[OUTBOX-SPOOL] Admin Server unreachable. Event saved to disk: {}", fileName);
            return true;
        } catch (Exception e) {
            log.error("Failed to spool event to file {}: {}", fileName, e.getMessage());
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception cleanupFailure) {
                log.warn("Failed to clean up partial spool write");
            }
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

        File[] files = spoolPath.toFile().listFiles((dir, name) -> name.startsWith("event_") && name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        // Sắp xếp theo thứ tự thời gian xảy ra sự kiện (FIFO)
        Arrays.sort(files, Comparator.comparing(File::getName));
        log.info("[OUTBOX-DRAIN] Found {} spooled event(s). Attempting to deliver in FIFO order...", files.length);

        for (File file : files) {
            UsbEventPayload payload;
            try {
                payload = objectMapper.readValue(file, UsbEventPayload.class);
            } catch (Exception e) {
                // File hỏng cú pháp JSON thật sự, không thể parse -> xóa bỏ để tránh tắc nghẽn hàng đợi
                log.error("Corrupted event file {}: {}. Removing unparseable file.", file.getName(), e.getMessage());
                if (!file.delete()) {
                    log.warn("Could not delete corrupted event file: {}", file.getName());
                }
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
                    log.error("Outbox event {} belongs to a different endpoint identity; "
                            + "manual review required before delivery", file.getName());
                    break;
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
            } else {
                // Server từ chối hoặc unreachable -> dừng drain để bảo đảm thứ tự FIFO
                log.warn("[OUTBOX-DRAIN] Server rejected or unreachable for event {}. Pausing drain until next cycle.", file.getName());
                break;
            }
        }
        } // drainLock
    }
}
