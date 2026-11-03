package com.group.usbshield.agent.usbguard;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.List;

/** Member 2 — Week 2. Keep Spring's existing gateway bean name and M3 boolean API. */
@Component("realUsbGuardGateway") @Primary
public class AdaptiveUsbGuardGateway implements UsbGuardGateway {
    private final UsbGuardCliGateway gateway = new UsbGuardCliGateway();
    /** Independent local check; an offline Admin Server must not permit foreign ALLOW bypass. */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${usbshield.agent.foreign-allow-check-interval-ms:15000}")
    public void withdrawForeignAllows() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) return;
        try {
            gateway.enforceLocalAllowOwnership();
        } catch (RuntimeException failure) {
            // Never pretend this enforcement check succeeded; keep retrying.
            org.slf4j.LoggerFactory.getLogger(getClass()).error(
                    "USBGuard local foreign-rule enforcement FAILED (retry scheduled)", failure);
        }
    }

    @Override public boolean isAvailable() { return gateway.isAvailable(); }
    @Override public List<UsbDeviceInfo> listDevices() { return gateway.listDevices(); }
    @Override public List<UsbGuardRuleInfo> listRules() { return gateway.listRules(); }
    @Override public UsbGuardHealth health() { return gateway.health(); }
    @Override public boolean applyWhitelist(List<String> rules) {
        try { return gateway.applyWhitelist(rules); }
        catch (RuntimeException e) {
            // Member 3 PolicySyncService sees false and MUST NOT ACK.
            org.slf4j.LoggerFactory.getLogger(getClass()).error("USBGuard apply failed; leaving policy version unchanged",e);
            return false;
        }
    }
}
