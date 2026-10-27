package com.group.usbshield.agent.session;

import java.util.List;
import java.util.Objects;

public class LoginctlActiveUserResolver implements ActiveUserResolver {
    private final LoginctlGateway loginctl;

    public LoginctlActiveUserResolver(LoginctlGateway loginctl) {
        this.loginctl = Objects.requireNonNull(loginctl, "loginctl");
    }

    @Override
    public ActiveUserInfo resolveCurrentActiveUser() {
        try {
            List<String> sessionIds = loginctl.listSessionIds();
            if (sessionIds == null || sessionIds.stream().anyMatch(Objects::isNull)) {
                return ActiveUserInfo.unknown("loginctl-error");
            }
            if (sessionIds.size() != sessionIds.stream().distinct().count()) {
                return ActiveUserInfo.unknown("ambiguous-active-local-sessions");
            }
            List<SessionProperties> snapshot = sessionIds.stream().map(loginctl::showSession).toList();
            if (snapshot.stream().anyMatch(Objects::isNull)) {
                return ActiveUserInfo.unknown("loginctl-error");
            }
            return ActiveSessionSelector.select(snapshot);
        } catch (RuntimeException error) {
            return ActiveUserInfo.unknown("loginctl-error");
        }
    }
}
