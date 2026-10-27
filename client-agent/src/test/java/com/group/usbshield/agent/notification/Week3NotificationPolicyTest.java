package com.group.usbshield.agent.notification;

import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.UsbDeviceInfo;
import com.group.usbshield.agent.usbguard.UsbGuardPresenceEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Week3NotificationPolicyTest {
    private static final ActiveUserInfo USER = ActiveUserInfo.resolved("student01", 1001L,
            "3", "wayland", "seat0");

    private static UsbDeviceInfo device() {
        return new UsbDeviceInfo(11, "block", "0951", "1665", "", "USB", "test-hash",
                List.of("08:06:50"), true, "");
    }

    private static UsbGuardPresenceEvent insertion() {
        return new UsbGuardPresenceEvent(UsbGuardPresenceEvent.PresenceType.INSERT, "block", device());
    }

    @Test
    void rapidDuplicatesAreSuppressedButRemovalAllowsNewInsertion() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        Instant start = Instant.ofEpochMilli(10_000);
        assertTrue(policy.classify(insertion(), USER, start).isPresent());
        assertTrue(policy.classify(insertion(), USER, start.plusMillis(100)).isEmpty());
        policy.onRemoved(USER, device());
        assertTrue(policy.classify(insertion(), USER, start.plusMillis(200)).isPresent());
    }

    @Test
    void unknownAndNonGraphicalSessionsCannotReceiveNotification() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        assertTrue(policy.classify(insertion(), ActiveUserInfo.unknown("logout"), Instant.now()).isEmpty());
        assertTrue(policy.classify(insertion(), ActiveUserInfo.resolved("user", 1001L,
                "3", "tty", "seat0"), Instant.now()).isEmpty());
    }

    @Test
    void removalDuringLogoutClearsPreviousCooldown() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        Instant start = Instant.ofEpochMilli(15_000);
        assertTrue(policy.classify(insertion(), USER, start).isPresent());
        policy.onRemoved(ActiveUserInfo.unknown("logout"), device());
        assertTrue(policy.classify(insertion(), USER, start.plusMillis(100)).isPresent());
    }

    @Test
    void graphicalSeatOtherThanSeat0StillUsesResolvedUid() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        ActiveUserInfo user = ActiveUserInfo.resolved("student02", 1002L, "4", "wayland", "seat1");
        assertTrue(policy.classify(insertion(), user, Instant.now()).isPresent());
    }

    @Test
    void userSwitchDoesNotSuppressNewUsersPopup() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        Instant when = Instant.now();
        assertTrue(policy.classify(insertion(), USER, when).isPresent());
        ActiveUserInfo second = ActiveUserInfo.resolved("student02", 1002L, "4", "x11", "seat0");
        assertTrue(policy.classify(insertion(), second, when.plusMillis(1)).isPresent());
    }

    @Test
    void rejectsNegativeCooldown() {
        assertThrows(IllegalArgumentException.class, () -> new BlockNotificationPolicy(-1));
    }
}
