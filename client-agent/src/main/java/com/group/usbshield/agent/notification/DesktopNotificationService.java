package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import com.group.usbshield.agent.usbguard.UsbGuardPresenceEvent;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class DesktopNotificationService {
    private final AgentProperties properties;
    private final ClientNotificationPublisher publisher;
    private final BlockNotificationPolicy policy = new BlockNotificationPolicy();
    private final ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), task -> {
                Thread thread = new Thread(task, "usbshield-notification");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    @Autowired
    public DesktopNotificationService(AgentProperties properties, ClientNotificationPublisher publisher) {
        this.properties = properties;
        this.publisher = publisher;
    }

    public DesktopNotificationService(AgentProperties properties) {
        this(properties, new UnixSocketNotificationPublisher());
    }

    public void notifyBlockedUsb(ActiveUserInfo user, UsbDeviceInfo device) {
        if (!properties.isNotificationEnabled() || device == null) {
            return;
        }
        policy.classify(new UsbGuardPresenceEvent(UsbGuardPresenceEvent.PresenceType.INSERT,
                        device.state(), device), user, Instant.now())
                .ifPresent(notice -> queue.execute(() -> {
                    try {
                        publisher.publish(notice);
                    } catch (RuntimeException ignored) {
                    }
                }));
    }

    public void onUsbRemoved(ActiveUserInfo user, UsbDeviceInfo device) {
        policy.onRemoved(user, device);
    }

    @PreDestroy
    public void stop() {
        queue.shutdownNow();
    }
}
