package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
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
                    String currentEventType = "CONNECTED";

                    while (running && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        log.debug("[usbguard watch raw] {}", line);

                        if (line.startsWith("event=Insert")) {
                            currentEventType = "CONNECTED";
                        } else if (line.startsWith("event=Remove")) {
                            currentEventType = "DISCONNECTED";
                        } else if (line.startsWith("device_rule=")) {
                            handleDeviceRuleEvent(line, currentEventType);
                        }
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

    public void handleDeviceRuleEvent(String deviceRuleLine, String eventType) {
        try {
            UsbDeviceInfo deviceInfo = eventParser.parseDeviceLine(deviceRuleLine);
            if (deviceInfo == null) {
                return;
            }

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
