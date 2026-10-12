package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DesktopNotificationServiceTest {

    private AgentProperties properties;
    private DesktopNotificationService notificationService;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setNotificationEnabled(true);
        notificationService = new DesktopNotificationService(properties);
    }

    @Test
    @DisplayName("Bắn popup cảnh báo khi cắm USB bị BLOCKED - Chạy an toàn không ném Exception")
    void testNotifyBlockedUsbSuccess() {
        ActiveUserInfo user = ActiveUserInfo.builder()
                .username("student01")
                .uid(1001L)
                .sessionId("3")
                .sessionType("wayland")
                .seat("seat0")
                .build();

        UsbDeviceInfo device = UsbDeviceInfo.builder()
                .name("Kingston DataTraveler 3.0")
                .vendorId("0951")
                .productId("1666")
                .decision("BLOCKED")
                .massStorage(true)
                .build();

        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(user, device));
    }

    @Test
    @DisplayName("Khi không có active user (UNKNOWN) - Bỏ qua an toàn không lỗi")
    void testNotifyWithUnknownUser() {
        ActiveUserInfo unknownUser = ActiveUserInfo.builder()
                .username("UNKNOWN")
                .uid(null)
                .build();

        UsbDeviceInfo device = UsbDeviceInfo.builder()
                .name("Unknown Flash")
                .decision("BLOCKED")
                .build();

        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(unknownUser, device));
    }

    @Test
    @DisplayName("Khi tắt cấu hình thông báo (notificationEnabled=false) - Không bắn thông báo")
    void testNotificationDisabled() {
        properties.setNotificationEnabled(false);

        ActiveUserInfo user = ActiveUserInfo.builder()
                .username("student01")
                .uid(1001L)
                .build();

        UsbDeviceInfo device = UsbDeviceInfo.builder()
                .name("Kingston Flash")
                .decision("BLOCKED")
                .build();

        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(user, device));
    }
}
