package com.group.usbshield.agent.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveUserInfoTest {
    @Test
    void resolvedRecordCarriesSessionAndTemporaryM3Getters() {
        ActiveUserInfo user = ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0");
        assertEquals("RESOLVED", user.status());
        assertEquals("student01", user.username());
        assertEquals(user.username(), user.getUsername()); // Bridge for M3 EventSenderService.
        assertEquals(user.uid(), user.getUid());
    }

    @Test
    void unknownUserCannotLeakPreviousSessionIdentity() {
        ActiveUserInfo user = new ActiveUserInfo("UNKNOWN", 1001L, "3", "wayland", "seat0", "RESOLVED", "ambiguous");
        assertEquals("UNKNOWN", user.status());
        assertEquals("UNKNOWN", user.username());
        assertNull(user.uid());
        assertNull(user.sessionId());
        assertEquals("ambiguous", user.reason());
    }
}
