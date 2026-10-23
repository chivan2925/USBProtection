package com.group.usbshield.agent.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Week3ActiveSessionTransitionTest {
    private static SessionProperties session(String id, String name, String uid,
                                             String active, String seat, String type, String remote) {
        return new SessionProperties(id, name, uid, active, seat, type, remote);
    }

    private static class FakeLoginctl implements LoginctlGateway {
        private List<SessionProperties> sessions = new ArrayList<>();

        void replace(SessionProperties... values) {
            sessions = new ArrayList<>(List.of(values));
        }

        @Override
        public List<String> listSessionIds() {
            return sessions.stream().map(SessionProperties::sessionId).toList();
        }

        @Override
        public SessionProperties showSession(String id) {
            return sessions.stream().filter(value -> value.sessionId().equals(id)).findFirst().orElseThrow();
        }
    }

    @Test
    void activeUserSwitchUsesCurrentSnapshot() {
        FakeLoginctl fake = new FakeLoginctl();
        LoginctlActiveUserResolver resolver = new LoginctlActiveUserResolver(fake);
        fake.replace(session("2", "student01", "1001", "yes", "seat0", "wayland", "no"));
        assertEquals("student01", resolver.resolveCurrentActiveUser().username());
        fake.replace(session("2", "student01", "1001", "no", "seat0", "wayland", "no"),
                session("4", "student02", "1002", "yes", "seat0", "x11", "no"));
        ActiveUserInfo next = resolver.resolveCurrentActiveUser();
        assertEquals("student02", next.username());
        assertEquals(1002L, next.uid());
        assertEquals("4", next.sessionId());
    }

    @Test
    void logoutDropsPreviouslySelectedIdentity() {
        FakeLoginctl fake = new FakeLoginctl();
        LoginctlActiveUserResolver resolver = new LoginctlActiveUserResolver(fake);
        fake.replace(session("2", "student01", "1001", "yes", "seat0", "wayland", "no"));
        resolver.resolveCurrentActiveUser();
        fake.replace();
        ActiveUserInfo result = resolver.resolveCurrentActiveUser();
        assertEquals("UNKNOWN", result.username());
        assertNull(result.uid());
        assertNull(result.sessionId());
    }

    @Test
    void remoteOnlyIsNotAttributed() {
        FakeLoginctl fake = new FakeLoginctl();
        fake.replace(session("5", "remote", "1200", "yes", "", "tty", "yes"));
        assertEquals("no-active-local-session", new LoginctlActiveUserResolver(fake)
                .resolveCurrentActiveUser().reason());
    }

    @Test
    void activeGraphicalSeat0WinsOverLocalTty() {
        FakeLoginctl fake = new FakeLoginctl();
        fake.replace(session("1", "console", "1003", "yes", "seat0", "tty", "no"),
                session("2", "student01", "1001", "yes", "seat0", "wayland", "no"));
        assertEquals("student01", new LoginctlActiveUserResolver(fake)
                .resolveCurrentActiveUser().username());
    }

    @Test
    void twoActiveGraphicalSessionsAreAmbiguous() {
        FakeLoginctl fake = new FakeLoginctl();
        fake.replace(session("1", "student01", "1001", "yes", "seat0", "x11", "no"),
                session("2", "student02", "1002", "yes", "seat0", "wayland", "no"));
        assertEquals("ambiguous-active-local-sessions", new LoginctlActiveUserResolver(fake)
                .resolveCurrentActiveUser().reason());
    }

    @Test
    void malformedAndOverflowUidCannotBeAttributed() {
        for (String uid : List.of("-1", "x123", "999999999999999999999999999")) {
            FakeLoginctl fake = new FakeLoginctl();
            fake.replace(session("2", "student01", uid, "yes", "seat0", "wayland", "no"));
            ActiveUserInfo result = new LoginctlActiveUserResolver(fake).resolveCurrentActiveUser();
            assertEquals("UNKNOWN", result.status(), uid);
            assertNull(result.uid(), uid);
        }
    }

    @Test
    void duplicateSessionIdsFailClosed() {
        FakeLoginctl fake = new FakeLoginctl();
        fake.replace(session("2", "student01", "1001", "yes", "seat0", "x11", "no"),
                session("2", "student02", "1002", "yes", "seat0", "x11", "no"));
        assertEquals("ambiguous-active-local-sessions", new LoginctlActiveUserResolver(fake)
                .resolveCurrentActiveUser().reason());
    }

    @Test
    void compatibilityCandidateSelectionUsesIdenticalRules() {
        LinuxActiveUserResolver resolver = new LinuxActiveUserResolver();
        var input = List.of(new LinuxActiveUserResolver.SessionCandidate(
                        "1", "console", 1003L, "tty", "seat0", true, false),
                new LinuxActiveUserResolver.SessionCandidate(
                        "2", "student01", 1001L, "wayland", "seat0", true, false));
        assertEquals("student01", resolver.evaluateCandidates(input).username());
        assertEquals("invalid-session-identity", resolver.evaluateCandidates(List.of(
                new LinuxActiveUserResolver.SessionCandidate(
                        "2", "student01", null, "wayland", "seat0", true, false))).reason());
    }

    @Test
    void nullSessionsAndInvalidNameAreUnknown() {
        LinuxActiveUserResolver resolver = new LinuxActiveUserResolver();
        assertEquals("UNKNOWN", resolver.evaluateCandidates(null).status());
        FakeLoginctl fake = new FakeLoginctl();
        fake.replace(session("2", "UNKNOWN", "1001", "yes", "seat0", "wayland", "no"));
        assertEquals("invalid-session-identity", new LoginctlActiveUserResolver(fake)
                .resolveCurrentActiveUser().reason());
    }
}
