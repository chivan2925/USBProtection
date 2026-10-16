package com.group.usbshield.agent.usbguard;

/** Member 2 — Week 2: USBGuard reachability result (guide section 11). */
public record UsbGuardHealth(boolean reachable, String message) {
    public static UsbGuardHealth up() {
        return new UsbGuardHealth(true, "USBGuard reachable");
    }

    public static UsbGuardHealth down(String message) {
        return new UsbGuardHealth(false, message);
    }
}
