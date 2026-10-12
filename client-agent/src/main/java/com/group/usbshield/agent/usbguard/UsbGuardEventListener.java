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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsbGuardEventListener {

    private final UsbGuardEventParser eventParser;
    private final ActiveUserResolver activeUserResolver;
    private final EndpointManager endpointManager;
    private final SystemInfoProvider systemInfoProvider;
    private final EventSenderService eventSenderService;
    private final DesktopNotificationService desktopNotificationService;
    public enum WatchSection {
        NONE,
        PRESENCE_CHANGED,
        POLICY_CHANGED,
        POLICY_APPLIED
    }

    private WatchSection currentSection = WatchSection.NONE;
    private String currentEventType = "CONNECTED";
    private final java.util.Map<String, Long> recentEventCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private Process watchProcess;

    @PostConstruct
    public void startListening() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            log.info("[UsbGuardEventListener] Non-Linux environment detected ('{}') -> Live 'usbguard watch' inactive (simulation mode active)", os);
            return;
        }

        executor.submit(this::watchLoop);
    }

    private void watchLoop() {
        log.info("[UsbGuardEventListener] Starting real-time USBGuard event listener thread via 'usbguard watch'...");

        while (running) {
            try {
                ProcessBuilder pb = new ProcessBuilder("usbguard", "watch");
                pb.redirectErrorStream(true);
                watchProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(watchProcess.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        processWatchLine(line);
                    }
                }

                int exitCode = watchProcess.waitFor();
                log.warn("[UsbGuardEventListener] 'usbguard watch' process exited with code {}. Restarting in 5s...", exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[UsbGuardEventListener] Error during watch execution: {}. Retrying in 5s...", e.getMessage());
            }

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Phân tích từng dòng từ usbguard watch và lọc chính xác điểm phát sự kiện.
     */
    public void processWatchLine(String line) {
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("[IPC]")) return;

        log.debug("[usbguard watch raw] {}", line);

        if (line.startsWith("[device] PresenceChanged")) {
            currentSection = WatchSection.PRESENCE_CHANGED;
            currentEventType = "CONNECTED";
        } else if (line.startsWith("[device] PolicyChanged")) {
            currentSection = WatchSection.POLICY_CHANGED;
        } else if (line.startsWith("[device] PolicyApplied")) {
            currentSection = WatchSection.POLICY_APPLIED;
        } else if (line.startsWith("event=Insert")) {
            currentEventType = "CONNECTED";
        } else if (line.startsWith("event=Remove")) {
            currentEventType = "DISCONNECTED";
        } else if (line.startsWith("device_rule=")) {
            // Chỉ gửi sự kiện tại thời điểm quyết định cuối cùng:
            // 1. Cắm USB: chỉ xử lý khi đã có kết quả [device] PolicyApplied
            // 2. Rút USB: chỉ xử lý tại [device] PresenceChanged với event=Remove
            boolean shouldHandle = (currentSection == WatchSection.POLICY_APPLIED && "CONNECTED".equals(currentEventType))
                    || (currentSection == WatchSection.PRESENCE_CHANGED && "DISCONNECTED".equals(currentEventType));

            if (shouldHandle) {
                handleDeviceRuleEvent(line, currentEventType);
            } else {
                log.debug("[UsbGuardEventListener] Skipping intermediate device_rule in section {}: {}", currentSection, line);
            }
        }
    }

    public void handleDeviceRuleEvent(String deviceRuleLine, String eventType) {
        try {
            UsbDeviceInfo deviceInfo = eventParser.parseDeviceLine(deviceRuleLine);
            if (deviceInfo == null) {
                return;
            }

            // Deduplication: Ngăn chặn gửi trùng lặp nếu sự kiện tương tự xuất hiện trong vòng 1.5 giây
            String dedupKey = deviceInfo.getVendorId() + ":" + deviceInfo.getProductId() + ":" + deviceInfo.getSerial() + ":" + eventType + ":" + deviceInfo.getDecision();
            long now = System.currentTimeMillis();
            Long lastTime = recentEventCache.get(dedupKey);
            if (lastTime != null && (now - lastTime) < 1500) {
                log.debug("[UsbGuardEventListener] Deduplicating rapid repeated event for key: {}", dedupKey);
                return;
            }
            recentEventCache.put(dedupKey, now);

            // Phân giải user active ngay tại thời điểm xảy ra sự kiện
            ActiveUserInfo activeUser = activeUserResolver.resolveCurrentActiveUser();

            UsbEventPayload payload = UsbEventPayload.builder()
                    .endpointId(endpointManager.getEndpointId())
                    .hostname(systemInfoProvider.getHostname())
                    .linuxUsername(activeUser.getUsername())
                    .linuxUid(activeUser.getUid())
                    .sessionId(activeUser.getSessionId())
                    .sessionType(activeUser.getSessionType())
                    .seat(activeUser.getSeat())
                    .device(deviceInfo.toContractDeviceMap())
                    .eventType(eventType != null ? eventType : "CONNECTED")
                    .decision(deviceInfo.getDecision())
                    .occurredAt(Instant.now())
                    .build();

            log.info("[UsbGuardEventListener] REAL EVENT: {} device '{}' ({} - {}) by user '{}' -> Decision: {}",
                    payload.getEventType(), deviceInfo.getName(), deviceInfo.getVendorId(), deviceInfo.getProductId(),
                    payload.getLinuxUsername(), payload.getDecision());

            // Nếu thiết bị bị chặn (BLOCKED), bắn popup cảnh báo lên màn hình người dùng
            if ("BLOCKED".equalsIgnoreCase(deviceInfo.getDecision())) {
                desktopNotificationService.notifyBlockedUsb(activeUser, deviceInfo);
            }

            eventSenderService.sendPayload(payload);
        } catch (Exception e) {
            log.error("[UsbGuardEventListener] Failed to process device event: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        if (watchProcess != null && watchProcess.isAlive()) {
            watchProcess.destroy();
        }
        executor.shutdownNow();
    }
}
