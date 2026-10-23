package com.group.usbshield.agent.session;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LinuxActiveUserResolver {
    private final LoginctlActiveUserResolver delegate;

    public LinuxActiveUserResolver() {
        this.delegate = new LoginctlActiveUserResolver(new ProcessLoginctlGateway());
    }

    public ActiveUserInfo resolveActiveUser() {
        return delegate.resolveCurrentActiveUser();
    }

    public record SessionCandidate(String sessionId, String username, Long uid,
                                   String sessionType, String seat, boolean active, boolean remote) {
    }

    public ActiveUserInfo evaluateCandidates(List<SessionCandidate> input) {
        if (input == null) {
            return ActiveUserInfo.unknown("no-active-local-session");
        }
        return ActiveSessionSelector.select(input.stream()
                .map(candidate -> candidate == null ? null : new SessionProperties(
                        candidate.sessionId(), candidate.username(),
                        candidate.uid() == null ? "" : Long.toString(candidate.uid()),
                        candidate.active() ? "yes" : "no", candidate.seat(),
                        candidate.sessionType(), candidate.remote() ? "yes" : "no"))
                .toList());
    }
}
