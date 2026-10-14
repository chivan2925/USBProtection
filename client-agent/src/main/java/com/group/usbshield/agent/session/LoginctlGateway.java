package com.group.usbshield.agent.session;
import java.util.List;
/** Member 2 — Week 2: injectable logind read-only command boundary. */
public interface LoginctlGateway {
    List<String> listSessionIds();
    SessionProperties showSession(String sessionId);
}
