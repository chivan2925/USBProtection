package com.group.usbshield.agent.session;

/**
 * Member 2 — Week 2: immutable local-session attribution result (guide section 5).
 *
 * <p>The deprecated JavaBean getters are a temporary compatibility bridge for
 * M3-owned EventSenderService; M2 call sites use record accessors instead.
 * M3 may remove the bridge once its own transport code migrates.</p>
 */
public record ActiveUserInfo(
        String username,
        Long uid,
        String sessionId,
        String sessionType,
        String seat,
        String status,
        String reason
) {
    public ActiveUserInfo {
        if (username == null || username.isBlank() || "UNKNOWN".equalsIgnoreCase(username)) {
            username = "UNKNOWN";
            uid = null;
            sessionId = null;
            sessionType = null;
            seat = null;
            status = "UNKNOWN";
        } else if (status == null || status.isBlank()) {
            status = "RESOLVED";
        }
    }

    public static ActiveUserInfo resolved(String username, Long uid, String sessionId, String sessionType, String seat) {
        return new ActiveUserInfo(username, uid, sessionId, sessionType, seat, "RESOLVED", null);
    }

    public static ActiveUserInfo unknown(String reason) {
        return new ActiveUserInfo("UNKNOWN", null, null, null, null, "UNKNOWN", reason);
    }

    // Compatibility only for M3-owned EventSenderService.java; do not add a mutable builder.
    @Deprecated public String getUsername() { return username; }
    @Deprecated public Long getUid() { return uid; }
    @Deprecated public String getSessionId() { return sessionId; }
    @Deprecated public String getSessionType() { return sessionType; }
    @Deprecated public String getSeat() { return seat; }
}
