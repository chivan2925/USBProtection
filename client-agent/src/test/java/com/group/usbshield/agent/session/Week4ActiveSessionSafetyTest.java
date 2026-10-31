package com.group.usbshield.agent.session;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Week4ActiveSessionSafetyTest {
    private static SessionProperties session(String id, String username, String uid,
                                             String active, String seat, String type, String remote) {
        return new SessionProperties(id, username, uid, active, seat, type, remote);
    }

    private static class FakeLoginctl implements LoginctlGateway {
        private final List<SessionProperties> sessions;

        FakeLoginctl(SessionProperties... sessions) {
            this.sessions = Arrays.asList(sessions);
        }

        @Override
        public List<String> listSessionIds() {
            return sessions.stream().map(SessionProperties::sessionId).toList();
        }

        @Override
        public SessionProperties showSession(String id) {
            return sessions.stream().filter(s -> s.sessionId().equals(id)).findFirst().orElseThrow();
        }
    }

    @Test
    void userSwitchUsesOnlyCurrentActiveLocalSession() {
        SessionProperties loggedOut = session("2", "student01", "1001", "no", "seat0", "wayland", "no");
        SessionProperties newUser = session("4", "student02", "1002", "yes", "seat0", "wayland", "no");
        ActiveUserInfo selected = new LoginctlActiveUserResolver(new FakeLoginctl(loggedOut, newUser))
                .resolveCurrentActiveUser();
        assertEquals("student02", selected.username());
        assertEquals(1002L, selected.uid());
        assertEquals("4", selected.sessionId());
    }

    @Test
    void twoActiveGraphicalSessionsAreAmbiguous() {
        ActiveUserInfo selected = new LoginctlActiveUserResolver(new FakeLoginctl(
                session("2", "student01", "1001", "yes", "seat0", "wayland", "no"),
                session("3", "student02", "1002", "yes", "seat0", "x11", "no")))
                .resolveCurrentActiveUser();
        assertEquals("UNKNOWN", selected.username());
        assertEquals("ambiguous-active-local-sessions", selected.reason());
        assertNull(selected.uid());
    }

    @Test
    void graphicalSeat0HasPriorityOverLocalTtyWhenBothActive() {
        ActiveUserInfo selected = new LoginctlActiveUserResolver(new FakeLoginctl(
                session("1", "console", "1003", "yes", "seat0", "tty", "no"),
                session("2", "student01", "1001", "yes", "seat0", "wayland", "no")))
                .resolveCurrentActiveUser();
        assertEquals("student01", selected.username());
        assertEquals("wayland", selected.sessionType());
    }

    @Test
    void remoteOnlyAndLoggedOutSessionsAreNotGuessed() {
        ActiveUserInfo selected = new LoginctlActiveUserResolver(new FakeLoginctl(
                session("2", "student01", "1001", "yes", "", "tty", "yes"),
                session("3", "student02", "1002", "no", "seat0", "wayland", "no")))
                .resolveCurrentActiveUser();
        assertEquals("UNKNOWN", selected.status());
        assertEquals("no-active-local-session", selected.reason());
    }

    @Test
    void singleLocalTtyCanStillBeAttributedWithoutClaimingGuiDelivery() {
        ActiveUserInfo selected = new LoginctlActiveUserResolver(new FakeLoginctl(
                session("5", "console", "1001", "yes", "seat0", "tty", "no")))
                .resolveCurrentActiveUser();
        assertEquals("console", selected.username());
        assertEquals("tty", selected.sessionType());
    }

    @Test
    void malformedSessionIdAndUnsafeUsernameAreUnknown() {
        ActiveUserInfo badId = new LoginctlActiveUserResolver(new FakeLoginctl(
                session("2\n3", "student01", "1001", "yes", "seat0", "wayland", "no")))
                .resolveCurrentActiveUser();
        assertEquals("UNKNOWN", badId.username());
        assertEquals("loginctl-error", badId.reason());
        ActiveUserInfo badName = ActiveSessionSelector.select(List.of(
                session("2", "student\n01", "1001", "yes", "seat0", "wayland", "no")));
        assertEquals("invalid-session-identity", badName.reason());
    }

    @Test
    void logoutBetweenEnumerationAndReadReturnsUnknown() {
        LoginctlGateway gateway = new LoginctlGateway() {
            @Override
            public List<String> listSessionIds() {
                return List.of("2");
            }

            @Override
            public SessionProperties showSession(String id) {
                throw new IllegalStateException("session disappeared");
            }
        };
        ActiveUserInfo selected = new LoginctlActiveUserResolver(gateway).resolveCurrentActiveUser();
        assertEquals("UNKNOWN", selected.status());
        assertEquals("loginctl-error", selected.reason());
    }

    @Test
    void invalidUidAndConflictingActiveTtysAreUnknown() {
        ActiveUserInfo badUid = ActiveSessionSelector.select(List.of(
                session("2", "student01", "999999999999999999999", "yes", "seat0", "wayland", "no")));
        assertEquals("invalid-session-identity", badUid.reason());
        ActiveUserInfo conflicting = ActiveSessionSelector.select(List.of(
                session("2", "student01", "1001", "yes", "seat0", "tty", "no"),
                session("3", "student02", "1002", "yes", "seat1", "tty", "no")));
        assertEquals("ambiguous-active-local-sessions", conflicting.reason());
    }
}
