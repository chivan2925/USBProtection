package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.usbguard.UsbGuardGateway;
import com.group.usbshield.agent.usbguard.UsbGuardHealth;
import com.group.usbshield.agent.usbguard.UsbGuardRuleBuilder;
import com.group.usbshield.agent.usbguard.UsbGuardRuleInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.group.usbshield.agent.usbguard.DeviceFingerprint;
import java.util.Locale;

/** Agent-owned evidence. HEALTHY requires real daemon inspection AND recent policy verification. */
@Component
public class UsbGuardHealthProbe {
    private final UsbGuardGateway gateway;
    private final boolean linux;
    // Null until this process has verified and durably persisted a complete policy.
    private volatile Map<String, String> lastVerifiedManagedRules;

    @Autowired
    public UsbGuardHealthProbe(UsbGuardGateway gateway) {
        this(gateway, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux"));
    }

    // Deterministic unit tests do not need to mutate global os.name.
    UsbGuardHealthProbe(UsbGuardGateway gateway, boolean linux) {
        this.gateway = gateway;
        this.linux = linux;
    }

    /** Only called after complete reconciliation and durable version persistence. */
    public void recordAppliedHashes(List<String> hashes) {
        var builder = new UsbGuardRuleBuilder();
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String hash : hashes) {
            String rule = builder.allowRule(new DeviceFingerprint(null, null, null, hash));
            if (wanted.putIfAbsent(hash, rule) != null)
                throw new IllegalArgumentException("Duplicate applied HASH");
        }
        lastVerifiedManagedRules = Map.copyOf(wanted);
    }

    private static String definition(String rawRule) {
        if (rawRule == null || !rawRule.contains(":"))
            throw new IllegalArgumentException("Malformed USBGuard rule");
        return rawRule.substring(rawRule.indexOf(':') + 1).strip().replaceAll("\\s+", " ");
    }

    public String measure(boolean policyRecentlyVerified) {
        if (!linux)
            return "UNKNOWN"; // never treat the Windows mock as live USB protection
        try {
            UsbGuardHealth health = gateway.health();
            if (health == null) return "UNKNOWN";
            if (!health.reachable()) {
                return health.message() != null && health.message().startsWith("Unsafe ImplicitPolicyTarget")
                        ? "DEGRADED" : "UNAVAILABLE";
            }
            List<UsbGuardRuleInfo> rules = gateway.listRules();
            if (rules == null || rules.stream().noneMatch(UsbGuardRuleInfo::isBaseNonStorageRule))
                return "DEGRADED";
            Map<String, String> expected = lastVerifiedManagedRules;
            if (!policyRecentlyVerified || expected == null) return "DEGRADED";
            Map<String, String> actual = new LinkedHashMap<>();
            for (UsbGuardRuleInfo rule : rules) {
                if (rule.isBaseNonStorageRule()) continue;
                boolean managed = rule.rawRule() != null &&
                        rule.rawRule().contains("label \"" + UsbGuardRuleBuilder.MARKER + "\"");
                if ("allow".equalsIgnoreCase(rule.target()) && !managed) return "DEGRADED";
                if (managed) {
                    if (!"allow".equalsIgnoreCase(rule.target()) || rule.hash() == null)
                        return "DEGRADED";
                    String actualDefinition;
                    try { actualDefinition = definition(rule.rawRule()); }
                    catch (IllegalArgumentException malformedRule) { return "DEGRADED"; }
                    if (actual.putIfAbsent(rule.hash(), actualDefinition) != null)
                        return "DEGRADED";
                }
            }
            if (actual.size() != expected.size()) return "DEGRADED";
            for (var desired : expected.entrySet()) {
                String current = actual.get(desired.getKey());
                if (current == null || !current.equals(desired.getValue())) return "DEGRADED";
            }
            return "HEALTHY";
        } catch (RuntimeException failure) {
            return "UNAVAILABLE";
        }
    }
}
