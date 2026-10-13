package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesktopNotificationService {

    private final AgentProperties properties;

    /**
     * Bắn popup/notification lên màn hình desktop của người dùng khi cắm USB bị chặn.
     */
    public void notifyBlockedUsb(ActiveUserInfo activeUser, UsbDeviceInfo deviceInfo) {
        if (!properties.isNotificationEnabled()) {
            log.debug("[DesktopNotification] Notifications are disabled in configuration.");
            return;
        }

        String deviceName = (deviceInfo != null && deviceInfo.name() != null && !deviceInfo.name().isBlank())
                ? deviceInfo.name()
                : "USB Mass Storage";

        String title = "USBShield - Cảnh báo bảo mật";
        String message = String.format("Thiết bị '%s' đã bị CHẶN theo chính sách an toàn thông tin của hệ thống!", deviceName);

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            log.info("[DesktopNotification] [SIMULATION - NON-LINUX] Popup notification: [{}] {}", title, message);
            return;
        }

        if (activeUser == null || activeUser.username() == null || "UNKNOWN".equalsIgnoreCase(activeUser.username()) || activeUser.uid() == null) {
            log.warn("[DesktopNotification] Cannot display popup: No active graphical user resolved.");
            return;
        }

        String username = activeUser.username();
        Long uid = activeUser.uid();
        log.info("[DesktopNotification] Sending desktop notification popup to active user '{}' (UID={})...", username, uid);

        try {
            // Lệnh chạy notify-send dưới session D-Bus của user trên Ubuntu (Wayland / X11)
            // Cú pháp: sudo -n -u <username> DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/<uid>/bus notify-send ...
            String busAddress = "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" + uid + "/bus";
            List<String> cmd = List.of(
                    "bash", "-c",
                    String.format(
                            "sudo -n -u %s %s notify-send -u critical -i drive-removable-media -a 'USBShield' '%s' '%s' || %s notify-send -u critical -i drive-removable-media -a 'USBShield' '%s' '%s'",
                            username, busAddress, title, message,
                            busAddress, title, message
                    )
            );

            Process process = new ProcessBuilder(cmd).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                log.info("[DesktopNotification] Popup notification displayed successfully for user '{}'.", username);
            } else {
                log.warn("[DesktopNotification] notify-send process completed with exit code: {}", finished ? process.exitValue() : "TIMEOUT");
            }
        } catch (Exception e) {
            log.error("[DesktopNotification] Failed to send desktop popup notification: {}", e.getMessage());
        }
    }
}
