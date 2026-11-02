package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** PolicyApplied drives invocation; the publisher has no delay or USBGuard polling. */
class UbuntuFinalStateNotificationTest {
    private static final ActiveUserInfo USER = ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0");
    private static UsbDeviceInfo device(String state) {
        return new UsbDeviceInfo(16, state, "0951", "1665", "serial", "DataTraveler 2.0", "hash-A",
                List.of("08:06:50"), true, "");
    }
    private static AgentProperties enabled() {
        AgentProperties props = new AgentProperties();
        props.setNotificationEnabled(true);
        return props;
    }

    @Test
    void definitiveBlockedDeviceNotifiesWithoutArtificialTimer() throws Exception {
        CountDownLatch displayed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        DesktopNotificationService service = new DesktopNotificationService(enabled(), notice -> {
            calls.incrementAndGet();
            displayed.countDown();
            return ClientNotificationPublisher.Delivery.DELIVERED;
        });
        try {
            service.notifyBlockedUsb(USER, device("block"));
            assertTrue(displayed.await(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { service.stop(); }
    }

    @Test
    void effectiveAllowedTargetNeverProducesBlockNotification() {
        AtomicInteger calls = new AtomicInteger();
        DesktopNotificationService service = new DesktopNotificationService(enabled(), notice -> {
            calls.incrementAndGet();
            return ClientNotificationPublisher.Delivery.DELIVERED;
        });
        try {
            service.notifyBlockedUsb(USER, device("allow"));
            assertEquals(0, calls.get());
        } finally { service.stop(); }
    }

    @Test
    void nonGraphicalUserNeverReceivesPopup() {
        AtomicInteger calls = new AtomicInteger();
        DesktopNotificationService service = new DesktopNotificationService(enabled(), notice -> {
            calls.incrementAndGet();
            return ClientNotificationPublisher.Delivery.DELIVERED;
        });
        try {
            service.notifyBlockedUsb(ActiveUserInfo.unknown("no desktop"), device("block"));
            assertEquals(0, calls.get());
        } finally { service.stop(); }
    }

    @Test
    void publisherFailureNeverBreaksEnforcementOrCaller() throws InterruptedException {
        CountDownLatch attempted = new CountDownLatch(1);
        DesktopNotificationService service = new DesktopNotificationService(enabled(), notice -> {
            attempted.countDown();
            throw new IllegalStateException("unavailable notifier");
        });
        try {
            assertDoesNotThrow(() -> service.notifyBlockedUsb(USER, device("block")));
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
        } finally { service.stop(); }
    }
}
