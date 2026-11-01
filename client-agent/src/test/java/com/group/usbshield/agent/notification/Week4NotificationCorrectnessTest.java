package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import com.group.usbshield.agent.usbguard.UsbGuardPresenceEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Week4NotificationCorrectnessTest {
    private static final ActiveUserInfo USER = ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");

    private static UsbDeviceInfo device(String hash, String state, boolean storage) {
        return new UsbDeviceInfo(11, state, "0951", "1665", "", "Flash", hash,
                List.of(storage ? "08:06:50" : "03:01:02"), storage, "");
    }

    private static UsbGuardPresenceEvent event(UsbGuardPresenceEvent.PresenceType type,
                                               String target, UsbDeviceInfo device) {
        return new UsbGuardPresenceEvent(type, target, device);
    }

    @Test
    void classificationUsesPresenceTargetNotStaleDeviceRuleTarget() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        UsbDeviceInfo stale = device("hash-A", "allow", true);
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", stale),
                USER, Instant.ofEpochMilli(10_000)).isPresent());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "allow", stale),
                USER, Instant.ofEpochMilli(11_000)).isEmpty());
    }

    @Test
    void rejectsAllowedHidRemovalAndUnknownOrTtySessions() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        Instant now = Instant.ofEpochMilli(10_000);
        UsbDeviceInfo storage = device("hash-A", "block", true);
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "allow", storage), USER, now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block",
                device("hid", "block", false)), USER, now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.REMOVE, "block", storage), USER, now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "reject", storage),
                ActiveUserInfo.unknown("logout"), now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", storage),
                ActiveUserInfo.resolved("student01", 1001L, "3", "tty", "seat0"), now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", storage),
                ActiveUserInfo.resolved("root", 0L, "1", "wayland", "seat0"), now).isEmpty());
    }

    @Test
    void removalAndSwitchAllowNewInsertionButRepeatedInsertIsSuppressed() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        UsbDeviceInfo storage = device("hash-A", "block", true);
        Instant at = Instant.ofEpochMilli(10_000);
        UsbGuardPresenceEvent insert = event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", storage);
        assertTrue(policy.classify(insert, USER, at).isPresent());
        assertTrue(policy.classify(insert, USER, at.plusMillis(1)).isEmpty());
        ActiveUserInfo switched = ActiveUserInfo.resolved("student02", 1002L, "4", "x11", "seat0");
        assertTrue(policy.classify(insert, switched, at.plusMillis(2)).isPresent());
        policy.onRemoved(ActiveUserInfo.unknown("logout"), storage);
        assertTrue(policy.classify(insert, USER, at.plusMillis(3)).isPresent());
    }

    @Test
    void cacheCannotGrowWithoutBoundUnderManyDevices() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        Instant now = Instant.ofEpochMilli(10_000);
        for (int i = 0; i < 1400; i++) {
            UsbDeviceInfo storage = device("hash-" + i, "block", true);
            assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", storage),
                    USER, now).isPresent());
            assertTrue(policy.trackedRecentCount() <= 1024);
        }
    }

    @Test
    void clockRollbackDoesNotGenerateDuplicatePopup() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        UsbGuardPresenceEvent insert = event(UsbGuardPresenceEvent.PresenceType.INSERT,
                "block", device("hash-A", "block", true));
        assertTrue(policy.classify(insert, USER, Instant.ofEpochMilli(10_000)).isPresent());
        assertTrue(policy.classify(insert, USER, Instant.ofEpochMilli(9_000)).isEmpty());
    }

    @Test
    void failedPublisherIsNonBlockingAndDoesNotThrowToCaller() throws InterruptedException {
        AgentProperties properties = new AgentProperties();
        properties.setNotificationEnabled(true);
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ClientNotificationPublisher publisher = notice -> {
            calls.incrementAndGet();
            attempted.countDown();
            throw new IllegalStateException("desktop helper unavailable");
        };
        DesktopNotificationService service = new DesktopNotificationService(properties, publisher);
        try {
            assertDoesNotThrow(() -> service.notifyBlockedUsb(USER, device("hash-A", "block", true)));
            assertTrue(attempted.await(3, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            service.stop();
        }
    }

    @Test
    void disabledNotificationDoesNotCallPublisher() {
        AgentProperties properties = new AgentProperties();
        properties.setNotificationEnabled(false);
        AtomicInteger calls = new AtomicInteger();
        DesktopNotificationService service = new DesktopNotificationService(properties, notice -> {
            calls.incrementAndGet();
            return ClientNotificationPublisher.Delivery.DELIVERED;
        });
        try {
            service.notifyBlockedUsb(USER, device("hash-A", "block", true));
            assertEquals(0, calls.get());
        } finally {
            service.stop();
        }
    }
}
