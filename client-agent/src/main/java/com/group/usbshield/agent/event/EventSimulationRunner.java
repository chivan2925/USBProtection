package com.group.usbshield.agent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Runner giả lập kích hoạt sự kiện cắm USB tự động sau khi Agent khởi động 10 giây.
 * Hữu ích để kiểm thử toàn diện luồng: Cắm USB -> Gắn user -> Gửi Server -> Fallback Spool.
 */
@Slf4j
@Component
public class EventSimulationRunner implements CommandLineRunner {

    private final EventSenderService eventSenderService;

    public EventSimulationRunner(EventSenderService eventSenderService) {
        this.eventSenderService = eventSenderService;
    }

    @Override
    public void run(String... args) {
        Thread simulationThread = new Thread(() -> {
            try {
                // Đợi 10 giây sau khi app boot để cho phép chu kỳ heartbeat/enroll thử chạy trước
                Thread.sleep(10000);

                log.info("[SIMULATION] Emulating a physical USB Flash Disk insertion event...");
                Map<String, Object> mockDevice = new HashMap<>();
                mockDevice.put("vendorId", "0951");
                mockDevice.put("productId", "1666");
                mockDevice.put("deviceName", "Kingston DataTraveler 3.0");
                mockDevice.put("serialNumber", "00187D0F55B6EC21391910A2");
                mockDevice.put("deviceHash", "c0ffee1234567890abcdef");

                eventSenderService.emitUsbEvent(mockDevice, "CONNECTED", "BLOCKED");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        simulationThread.setDaemon(true);
        simulationThread.setName("usb-simulation-worker");
        simulationThread.start();
    }
}
