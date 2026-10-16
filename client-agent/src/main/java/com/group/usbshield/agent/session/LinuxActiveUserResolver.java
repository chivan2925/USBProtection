package com.group.usbshield.agent.session;

import org.springframework.stereotype.Component;
import java.util.List;

/** Member 2 — Week 2: bridge existing M3 Spring bean to single hardened loginctl implementation. */
@Component
public class LinuxActiveUserResolver {
    private final LoginctlActiveUserResolver delegate;
    public LinuxActiveUserResolver() {this.delegate = new LoginctlActiveUserResolver(new ProcessLoginctlGateway());}
    public ActiveUserInfo resolveActiveUser() {return delegate.resolveCurrentActiveUser();}
    /** Existing M2 tests and Week 1 compatibility. */
    public record SessionCandidate(String sessionId,String username,Long uid,String sessionType,String seat,boolean active,boolean remote) {}
    public ActiveUserInfo evaluateCandidates(List<SessionCandidate> input) {
        List<SessionCandidate> candidates = input == null ? List.of() : input.stream().filter(c->c.active()&&!c.remote()).toList();
        if(candidates.isEmpty())return ActiveUserInfo.unknown("no-active-local-session");
        SessionCandidate selected;
        if(candidates.size()==1) selected=candidates.getFirst();
        else {
            List<SessionCandidate> graphical=candidates.stream().filter(c->"seat0".equalsIgnoreCase(c.seat())&&
                    ("wayland".equalsIgnoreCase(c.sessionType())||"x11".equalsIgnoreCase(c.sessionType()))).toList();
            if(graphical.size()!=1)return ActiveUserInfo.unknown("ambiguous-active-local-sessions");
            selected=graphical.getFirst();
        }
        return ActiveUserInfo.resolved(selected.username(),selected.uid(),selected.sessionId(),selected.sessionType(),selected.seat());
    }
}
