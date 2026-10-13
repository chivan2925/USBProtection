package com.group.usbshield.agent.usbguard;

/**
 * Member 2 — Week 2: identity for a specific USB device (guide section 9).
 * Prefer USBGuard hash and/or serial + VID/PID; never identify a device by name.
 * Validation and matching policy are implemented in later Week 2 steps.
 */
public record DeviceFingerprint(
        String vendorId,
        String productId,
        String serial,
        String hash
) {
}
