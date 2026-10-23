package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import com.group.usbshield.agent.usbguard.UsbGuardPresenceEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BlockNotificationPolicy {
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public BlockNotificationPolicy() {
        this(1500);
    }

    public BlockNotificationPolicy(long cooldownMillis) {
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException("cooldownMillis");
        }
        this.cooldownMillis = cooldownMillis;
    }

    public Optional<UsbBlockNotification> classify(UsbGuardPresenceEvent event, ActiveUserInfo user, Instant when) {
        if (event == null || event.device() == null || user == null || when == null) {
            return Optional.empty();
        }
        if (event.presenceType() != UsbGuardPresenceEvent.PresenceType.INSERT
                || !event.device().massStorage()) {
            return Optional.empty();
        }
        if (!"block".equalsIgnoreCase(event.target()) && !"reject".equalsIgnoreCase(event.target())) {
            return Optional.empty();
        }
        if (!"RESOLVED".equals(user.status()) || user.uid() == null || user.uid() < 0
                || user.username() == null || "UNKNOWN".equalsIgnoreCase(user.username())
                || user.sessionId() == null || user.sessionId().isBlank()
                || (!"wayland".equalsIgnoreCase(user.sessionType())
                && !"x11".equalsIgnoreCase(user.sessionType()))) {
            return Optional.empty();
        }
        String key = notificationKey(user, event.device());
        long now = when.toEpochMilli();
        if (cooldownMillis > 0) {
            Long last = recent.put(key, now);
            if (last != null && now >= last && now - last < cooldownMillis) {
                return Optional.empty();
            }
            if (recent.size() > 1024) {
                recent.entrySet().removeIf(entry -> now - entry.getValue() > cooldownMillis * 2);
            }
        }
        return Optional.of(new UsbBlockNotification(user.uid(), user.username(), deviceIdentity(event.device()),
                "USBShield — USB Mass Storage bị chặn",
                "Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.", when));
    }

    public void onRemoved(ActiveUserInfo user, UsbDeviceInfo device) {
        if (device != null) {
            String suffix = ":" + deviceIdentity(device);
            recent.keySet().removeIf(key -> key.endsWith(suffix));
        }
    }

    private static String notificationKey(ActiveUserInfo user, UsbDeviceInfo device) {
        return user.uid() + ":" + deviceIdentity(device);
    }

    private static String deviceIdentity(UsbDeviceInfo device) {
        String hash = device.hash();
        return hash == null || hash.isBlank()
                ? device.vendorId() + ":" + device.productId() + ":" + device.serial()
                : hash;
    }
}
