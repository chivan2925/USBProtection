package com.group.usbshield.agent.usbguard;

/** Member 2 — Week 2: parsed USBGuard policy rule (guide section 10). */
public record UsbGuardRuleInfo(
        long ruleId,
        String target,
        String vendorId,
        String productId,
        String serial,
        String hash,
        String rawRule
) {
    /** The baseline non-storage allow rule must survive whitelist reconciliation. */
    public boolean isBaseNonStorageRule() {
        return "allow".equalsIgnoreCase(target) && rawRule != null
                && rawRule.contains("none-of")
                && rawRule.contains("08:*:*");
    }
}
