package com.group.usbshield.agent.notification;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BlockNotificationPolicyTest {
 UsbDeviceInfo device(String state,String iface) {return new UsbDeviceInfo(11,state,"0951","1665","s","Kingston","hash",List.of(iface),iface.startsWith("08"),"");}
 UsbGuardPresenceEvent event(UsbGuardPresenceEvent.PresenceType type,String decision,String iface){return new UsbGuardPresenceEvent(type,decision,device(decision,iface));}
 private final ActiveUserInfo user=ActiveUserInfo.resolved("student01",1001L,"3","wayland","seat0");
 @Test void emitsOnceForBlockedStorageAndDeduplicates(){var rule=new BlockNotificationPolicy(1500);var e=event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50");assertTrue(rule.classify(e,user,Instant.ofEpochMilli(10_000)).isPresent());assertTrue(rule.classify(e,user,Instant.ofEpochMilli(10_001)).isEmpty());}
 @Test void ignoresNonStorageAllowedRemovalAndUnknownUser(){var rule=new BlockNotificationPolicy();var t=Instant.now();assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","03:01:02"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"allow","08:06:50"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.REMOVE,"block","08:06:50"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50"),ActiveUserInfo.unknown("no-active"),t).isEmpty());}
 @Test void doesNotSendToTTY(){var tty=ActiveUserInfo.resolved("user",1000L,"3","tty","seat0");assertTrue(new BlockNotificationPolicy().classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50"),tty,Instant.now()).isEmpty());}

    // Additional cooldown/session cases consolidated from the former Week 3 suite.
    private static final ActiveUserInfo USER_W3 = ActiveUserInfo.resolved("student01", 1001L,
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
        assertTrue(policy.classify(insertion(), USER_W3, start).isPresent());
        assertTrue(policy.classify(insertion(), USER_W3, start.plusMillis(100)).isEmpty());
        policy.onRemoved(USER_W3, device());
        assertTrue(policy.classify(insertion(), USER_W3, start.plusMillis(200)).isPresent());
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
        assertTrue(policy.classify(insertion(), USER_W3, start).isPresent());
        policy.onRemoved(ActiveUserInfo.unknown("logout"), device());
        assertTrue(policy.classify(insertion(), USER_W3, start.plusMillis(100)).isPresent());
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
        assertTrue(policy.classify(insertion(), USER_W3, when).isPresent());
        ActiveUserInfo second = ActiveUserInfo.resolved("student02", 1002L, "4", "x11", "seat0");
        assertTrue(policy.classify(insertion(), second, when.plusMillis(1)).isPresent());
    }

    @Test
    void rejectsNegativeCooldown() {
        assertThrows(IllegalArgumentException.class, () -> new BlockNotificationPolicy(-1));
    }

    // Final-target/dedup cases consolidated from the former Week 4 suite.
    private static final ActiveUserInfo USER_W4 = ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");

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
                USER_W4, Instant.ofEpochMilli(10_000)).isPresent());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "allow", stale),
                USER_W4, Instant.ofEpochMilli(11_000)).isEmpty());
    }

    @Test
    void rejectsAllowedHidRemovalAndUnknownOrTtySessions() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy();
        Instant now = Instant.ofEpochMilli(10_000);
        UsbDeviceInfo storage = device("hash-A", "block", true);
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "allow", storage), USER_W4, now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block",
                device("hid", "block", false)), USER_W4, now).isEmpty());
        assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.REMOVE, "block", storage), USER_W4, now).isEmpty());
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
        assertTrue(policy.classify(insert, USER_W4, at).isPresent());
        assertTrue(policy.classify(insert, USER_W4, at.plusMillis(1)).isEmpty());
        ActiveUserInfo switched = ActiveUserInfo.resolved("student02", 1002L, "4", "x11", "seat0");
        assertTrue(policy.classify(insert, switched, at.plusMillis(2)).isPresent());
        policy.onRemoved(ActiveUserInfo.unknown("logout"), storage);
        assertTrue(policy.classify(insert, USER_W4, at.plusMillis(3)).isPresent());
    }

    @Test
    void cacheCannotGrowWithoutBoundUnderManyDevices() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        Instant now = Instant.ofEpochMilli(10_000);
        for (int i = 0; i < 1400; i++) {
            UsbDeviceInfo storage = device("hash-" + i, "block", true);
            assertTrue(policy.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT, "block", storage),
                    USER_W4, now).isPresent());
            assertTrue(policy.trackedRecentCount() <= 1024);
        }
    }

    @Test
    void clockRollbackDoesNotGenerateDuplicatePopup() {
        BlockNotificationPolicy policy = new BlockNotificationPolicy(1500);
        UsbGuardPresenceEvent insert = event(UsbGuardPresenceEvent.PresenceType.INSERT,
                "block", device("hash-A", "block", true));
        assertTrue(policy.classify(insert, USER_W4, Instant.ofEpochMilli(10_000)).isPresent());
        assertTrue(policy.classify(insert, USER_W4, Instant.ofEpochMilli(9_000)).isEmpty());
    }
}
