package com.group.usbshield.agent.usbguard;

import java.util.Collection;
import java.util.List;

/** Read-only rule precedence check. Shares the fail-closed overlap principle
 * with UsbGuardPolicySynchronizer; must never mutate the daemon in health probes. */
public final class UsbGuardPolicyOrderSafety {
    private UsbGuardPolicyOrderSafety() {}

    public static boolean isSafe(List<UsbGuardRuleInfo> rules, Collection<String> expectedHashes) {
        if (rules == null || expectedHashes == null) return false;
        int baseIndex = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).isBaseNonStorageRule()) { baseIndex = i; break; }
        }
        if (baseIndex < 0) return false;
        for (int i = 0; i < baseIndex; i++) {
            if (isDeny(rules.get(i))) return false;
        }
        for (String hash : expectedHashes) {
            int grant = -1;
            for (int i = 0; i < rules.size(); i++) {
                UsbGuardRuleInfo rule = rules.get(i);
                if ("allow".equalsIgnoreCase(rule.target()) && hash.equals(rule.hash()) &&
                        rule.rawRule() != null && rule.rawRule().contains("label \"usbshield-managed\"")) {
                    grant = i; break;
                }
            }
            if (grant < 0) return false;
            UsbGuardRuleInfo wanted = rules.get(grant);
            for (int i = 0; i < grant; i++) {
                UsbGuardRuleInfo before = rules.get(i);
                if (isDeny(before) && mightOverlap(before, wanted)) return false;
            }
        }
        return true;
    }

    private static boolean isDeny(UsbGuardRuleInfo rule) {
        return "block".equalsIgnoreCase(rule.target()) || "reject".equalsIgnoreCase(rule.target());
    }

    private static boolean mightOverlap(UsbGuardRuleInfo left, UsbGuardRuleInfo right) {
        if (present(left.vendorId()) && present(left.productId()) &&
                present(right.vendorId()) && present(right.productId()) &&
                (!left.vendorId().equalsIgnoreCase(right.vendorId()) ||
                 !left.productId().equalsIgnoreCase(right.productId()))) return false;
        if (present(left.hash()) && present(right.hash()) && !left.hash().equals(right.hash())) return false;
        if (present(left.serial()) && present(right.serial()) && !left.serial().equals(right.serial())) return false;
        return true; // Unknown or wildcard attributes conservatively overlap.
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
