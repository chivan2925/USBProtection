package com.group.usbshield.agent.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinuxActiveUserResolverTest {

    private LinuxActiveUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LinuxActiveUserResolver();
    }

    @Test
    @DisplayName("Kịch bản 1: Có đúng 1 session active local -> Phải RESOLVED đúng user")
    void testSingleActiveLocalCandidate() {
        var candidate = new LinuxActiveUserResolver.SessionCandidate(
                "21", "usbdev", 1000L, "wayland", "seat0", true, false
        );

        ActiveUserInfo user = resolver.evaluateCandidates(List.of(candidate));

        assertNotNull(user);
        assertEquals("usbdev", user.getUsername());
        assertEquals(1000L, user.getUid());
        assertEquals("21", user.getSessionId());
        assertEquals("wayland", user.getSessionType());
        assertEquals("seat0", user.getSeat());
    }

    @Test
    @DisplayName("Kịch bản 2: Không có session nào active -> Phải UNKNOWN (không đoán mò)")
    void testNoCandidates() {
        ActiveUserInfo user = resolver.evaluateCandidates(Collections.emptyList());

        assertNotNull(user);
        assertEquals("UNKNOWN", user.getUsername());
        assertNull(user.getUid());
        assertNull(user.getSessionId());
    }

    @Test
    @DisplayName("Kịch bản 3: Có 1 graphical (seat0, wayland) và 1 tty -> Phải ưu tiên graphical")
    void testGraphicalFilterPreference() {
        var graphical = new LinuxActiveUserResolver.SessionCandidate(
                "2", "student01", 1001L, "wayland", "seat0", true, false
        );
        var tty = new LinuxActiveUserResolver.SessionCandidate(
                "5", "backup_user", 1002L, "tty", "seat0", true, false
        );

        ActiveUserInfo user = resolver.evaluateCandidates(List.of(graphical, tty));

        assertNotNull(user);
        assertEquals("student01", user.getUsername());
        assertEquals(1001L, user.getUid());
        assertEquals("wayland", user.getSessionType());
    }

    @Test
    @DisplayName("Kịch bản 4: Ambiguity - Có 2 session đồ họa cùng cạnh tranh trên seat0 -> Fail-safe UNKNOWN")
    void testAmbiguityFailSafe() {
        var user1 = new LinuxActiveUserResolver.SessionCandidate(
                "2", "student01", 1001L, "wayland", "seat0", true, false
        );
        var user2 = new LinuxActiveUserResolver.SessionCandidate(
                "3", "student02", 1002L, "x11", "seat0", true, false
        );

        ActiveUserInfo user = resolver.evaluateCandidates(List.of(user1, user2));

        assertNotNull(user);
        assertEquals("UNKNOWN", user.getUsername(), "Khi có tranh chấp 2 session đồ họa, bắt buộc trả về UNKNOWN");
        assertNull(user.getUid());
    }
}
