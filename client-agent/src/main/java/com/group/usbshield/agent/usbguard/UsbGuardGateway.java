package com.group.usbshield.agent.usbguard;

import java.util.List;

/**
 * Member 2 — Week 2: contract for USBGuard inspection (using UsbDeviceInfo records) and local policy enforcement.
 *
 * <p>The boolean {@code applyWhitelist(List<String>)} entry point is retained
 * for compatibility with Member 3's existing PolicySyncService. Member 2 and
 * Member 3 must agree on a typed policy-reconciliation result before replacing
 * that signature; {@code List<DeviceFingerprint>} cannot be overloaded against
 * {@code List<String>} because of Java type erasure.</p>
 *
 * <p>Inspection operations below are deliberately NOT IMPLEMENTED by the
 * Week 1 adapters; they fail explicitly until Member 2 implements the real
 * USBGuard CLI gateway in the subsequent Week 2 steps.</p>
 */
public interface UsbGuardGateway {
    boolean isAvailable();

    /**
     * Existing M3 integration entry point. Returns false when local apply fails.
     * Member 2 owns the real semantics; Member 3 owns the caller and policy ACK.
     */
    boolean applyWhitelist(List<String> whitelistFingerprints);

    /** Member 2 — Week 2: implement in UsbGuardCliGateway. */
    default List<UsbDeviceInfo> listDevices() {
        throw new UnsupportedOperationException("Member 2 Week 2: listDevices is not implemented");
    }

    /** Member 2 — Week 2: implement in UsbGuardCliGateway. */
    default List<UsbGuardRuleInfo> listRules() {
        throw new UnsupportedOperationException("Member 2 Week 2: listRules is not implemented");
    }

    /** Member 2 — Week 2: implement genuine daemon/IPC health reporting. */
    default UsbGuardHealth health() {
        throw new UnsupportedOperationException("Member 2 Week 2: health is not implemented");
    }
}
