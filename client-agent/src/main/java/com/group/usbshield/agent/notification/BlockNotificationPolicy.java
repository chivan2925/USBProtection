package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import com.group.usbshield.agent.usbguard.UsbGuardPresenceEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BlockNotificationPolicy {
    private static final int MAX_RECENT = 1024;
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

    public synchronized Optional<UsbBlockNotification> classify(UsbGuardPresenceEvent event, ActiveUserInfo user, Instant when) {
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
        if (!"RESOLVED".equals(user.status()) || user.uid() == null || user.uid() < 1000
                || user.username() == null || "UNKNOWN".equalsIgnoreCase(user.username())
                || user.sessionId() == null || user.sessionId().isBlank()
                || (!"wayland".equalsIgnoreCase(user.sessionType())
                && !"x11".equalsIgnoreCase(user.sessionType()))) {
            return Optional.empty();
        }
        String key = notificationKey(user, event.device());
        long now = when.toEpochMilli();
        if (cooldownMillis > 0) {
            Long last = recent.get(key);
            if (last != null && (now < last || now - last < cooldownMillis)) {
                return Optional.empty();
            }
            if (last == null && recent.size() >= MAX_RECENT) {
                recent.entrySet().removeIf(entry -> now < entry.getValue()
                        || now - entry.getValue() >= cooldownMillis);
                if (recent.size() >= MAX_RECENT) {
                    recent.entrySet().stream()
                            .min(Comparator.comparingLong(Map.Entry::getValue))
                            .ifPresent(oldest -> recent.remove(oldest.getKey(), oldest.getValue()));
                }
            }
            recent.put(key, now);
        }
        return Optional.of(new UsbBlockNotification(user.uid(), user.username(), deviceIdentity(event.device()),
                "USBShield — USB Mass Storage bị chặn",
                "Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.", when));
    }

    public synchronized void onRemoved(ActiveUserInfo user, UsbDeviceInfo device) {
        if (device != null) {
            String suffix = ":" + deviceIdentity(device);
            recent.keySet().removeIf(key -> key.endsWith(suffix));
        }
    }

    int trackedRecentCount() {
        return recent.size();
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
