package com.group.usbshield.agent.usbguard;

import java.util.regex.Pattern;

public record UsbGuardRuleInfo(
        long ruleId,
        String target,
        String vendorId,
        String productId,
        String serial,
        String hash,
        String rawRule
) {
    private static final Pattern BASE_NON_STORAGE = Pattern.compile(
            "^\\s*\\d+:\\s*allow\\s+with-interface\\s+none-of\\s*\\{\\s*08:\\*:\\*\\s*\\}\\s*$",
            Pattern.CASE_INSENSITIVE);

    public boolean isBaseNonStorageRule() {
        return "allow".equalsIgnoreCase(target) && rawRule != null
                && BASE_NON_STORAGE.matcher(rawRule).matches();
    }
}
