package com.group.usbshield.agent.session;

/** Member 2 — Week 2: machine-readable loginctl session properties. */
public record SessionProperties(String sessionId, String name, String user, String active, String seat, String type, String remote) {
    public boolean activeLocal() { return "yes".equalsIgnoreCase(active) && "no".equalsIgnoreCase(remote); }
    public boolean graphicalSeat0() {
        return "seat0".equalsIgnoreCase(seat) && ("wayland".equalsIgnoreCase(type) || "x11".equalsIgnoreCase(type));
    }
}
