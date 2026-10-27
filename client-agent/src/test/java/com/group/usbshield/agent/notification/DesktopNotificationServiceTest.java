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
    @DisplayName("Bắn popup BLOCKED sử dụng record bất biến")
    void testNotifyBlockedUsbSuccess() {
        ActiveUserInfo user = ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");
        UsbDeviceInfo device = new UsbDeviceInfo(-1, "block", "0951", "1666", "",
                "Kingston DataTraveler 3.0", "hash", java.util.List.of("08:06:50"), true, "block id 0951:1666");
        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(user, device));
    }

    @Test
    @DisplayName("Không có active user thì bỏ qua an toàn")
    void testNotifyWithUnknownUser() {
        ActiveUserInfo unknownUser = ActiveUserInfo.unknown("no-active-session");
        UsbDeviceInfo device = new UsbDeviceInfo(-1, "block", "", "", "", "Unknown Flash",
                "", java.util.List.of("08:06:50"), true, "block id 0000:0000");
        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(unknownUser, device));
    }

    @Test
    @DisplayName("Tắt notification không làm phát popup")
    void testNotificationDisabled() {
        properties.setNotificationEnabled(false);
        ActiveUserInfo user = ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");
        UsbDeviceInfo device = new UsbDeviceInfo(-1, "block", "0951", "1666", "", "Kingston Flash",
                "hash", java.util.List.of("08:06:50"), true, "block id 0951:1666");
        assertDoesNotThrow(() -> notificationService.notifyBlockedUsb(user, device));
    }
}
