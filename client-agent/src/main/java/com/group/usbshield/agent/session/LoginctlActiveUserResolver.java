package com.group.usbshield.agent.session;

import java.util.List;

/** Member 2 — Week 2: fail-safe unique active-local user resolution. */
public class LoginctlActiveUserResolver implements ActiveUserResolver {
    private final LoginctlGateway loginctl;
    public LoginctlActiveUserResolver(LoginctlGateway loginctl) {this.loginctl=loginctl;}
    @Override public ActiveUserInfo resolveCurrentActiveUser() {
        try {
            List<SessionProperties> active=loginctl.listSessionIds().stream().map(loginctl::showSession)
                    .filter(SessionProperties::activeLocal).toList();
            if (active.isEmpty()) return ActiveUserInfo.unknown("no-active-local-session");
            SessionProperties selected;
            if(active.size()==1) selected=active.getFirst();
            else {
                List<SessionProperties> graphical=active.stream().filter(SessionProperties::graphicalSeat0).toList();
                if (graphical.size()!=1) return ActiveUserInfo.unknown("ambiguous-active-local-sessions");
                selected=graphical.getFirst();
            }
            if(selected.name()==null || selected.name().isBlank() || selected.user()==null || !selected.user().matches("[0-9]+"))
                return ActiveUserInfo.unknown("invalid-session-identity");
            return ActiveUserInfo.resolved(selected.name(), Long.valueOf(selected.user()),selected.sessionId(),selected.type(),selected.seat());
        } catch (RuntimeException e) { return ActiveUserInfo.unknown("loginctl-error"); }
    }
}
