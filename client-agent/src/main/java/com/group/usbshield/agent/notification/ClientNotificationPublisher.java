package com.group.usbshield.agent.notification;
/** Member 2 — Week 2: local-only payload publisher. M3 provisions the socket/ACL and user helper startup. */
public interface ClientNotificationPublisher {
    enum Delivery { DELIVERED, SKIPPED_NO_GUI, FAILED }
    Delivery publish(UsbBlockNotification notice);
}
