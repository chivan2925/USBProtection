package com.group.usbshield.agent.usbguard;

/** Member 2 — Week 2: true PresenceChanged action; never emit PolicyChanged/PolicyApplied as insertion. */
public record UsbGuardPresenceEvent(PresenceType presenceType, String target, UsbDeviceInfo device) {
    public enum PresenceType { INSERT, REMOVE }
}
