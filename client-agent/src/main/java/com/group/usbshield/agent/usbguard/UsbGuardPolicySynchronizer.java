package com.group.usbshield.agent.usbguard;

import java.util.*;

/** Member 2 — Week 2: reconcile only USBShield-labeled allow rules; preserve base and foreign rules. */
public class UsbGuardPolicySynchronizer {
    private final UsbGuardCommandRunner runner;
    private final UsbGuardOutputParser parser;
    private final UsbGuardRuleBuilder builder;
    public UsbGuardPolicySynchronizer(UsbGuardCommandRunner runner, UsbGuardOutputParser parser, UsbGuardRuleBuilder builder) {
        this.runner = runner; this.parser = parser; this.builder = builder;
    }
    public void reconcile(List<DeviceFingerprint> desiredWhitelist) {
        Objects.requireNonNull(desiredWhitelist, "desiredWhitelist");
        CommandResult policyTarget=runner.run("get-parameter", "ImplicitPolicyTarget");
        requireSuccess(policyTarget, "get-parameter");
        if (!policyTarget.output().toLowerCase(Locale.ROOT).contains("block"))
            throw new IllegalStateException("ImplicitPolicyTarget is not block; refusing policy changes");
        Map<String,DeviceFingerprint> desired = new LinkedHashMap<>();
        for (DeviceFingerprint f : desiredWhitelist) desired.put(builder.key(f), f);
        List<UsbGuardRuleInfo> current = rules();
        if (current.stream().noneMatch(UsbGuardRuleInfo::isBaseNonStorageRule))
            throw new IllegalStateException("USBGuard base non-storage rule missing; refusing to modify policy");
        Map<String, UsbGuardRuleInfo> managed = new HashMap<>();
        for (UsbGuardRuleInfo rule : current) {
            if (!isManaged(rule)) continue;
            if (!"allow".equalsIgnoreCase(rule.target())) throw new IllegalStateException("Unexpected USBShield rule target");
            DeviceFingerprint f = new DeviceFingerprint(rule.vendorId(), rule.productId(), rule.serial(), rule.hash());
            String key = builder.key(f);
            if (managed.putIfAbsent(key, rule) != null) throw new IllegalStateException("Duplicate managed USBGuard rule; manual cleanup required");
        }
        // Unlabeled rules are never implicitly owned or deleted. Reject any conflicting identity.
        for (UsbGuardRuleInfo rule : current) {
            if (rule.isBaseNonStorageRule() || isManaged(rule) || !"allow".equalsIgnoreCase(rule.target())) continue;
            if ((rule.hash() == null || rule.hash().isBlank()) && (rule.serial() == null || rule.serial().isBlank())) continue;
            try {
                String key = builder.key(new DeviceFingerprint(rule.vendorId(), rule.productId(), rule.serial(), rule.hash()));
                if (desired.containsKey(key) || managed.containsKey(key))
                    throw new IllegalStateException("Unmanaged rule conflicts with USBShield whitelist: " + key);
            } catch (IllegalArgumentException ignored) { /* malformed foreign rule: never touch it */ }
        }
        for (var e : desired.entrySet()) if (!managed.containsKey(e.getKey()))
            requireSuccess(runner.run("append-rule", builder.allowRule(e.getValue())), "append-rule");
        for (var e : managed.entrySet()) if (!desired.containsKey(e.getKey()))
            requireSuccess(runner.run("remove-rule", Long.toString(e.getValue().ruleId())), "remove-rule");
        List<UsbGuardRuleInfo> after = rules();
        if (after.stream().noneMatch(UsbGuardRuleInfo::isBaseNonStorageRule)) throw new IllegalStateException("Base policy was lost");
        Set<String> actual = new HashSet<>();
        for (var rule : after) if (isManaged(rule)) actual.add(builder.key(new DeviceFingerprint(rule.vendorId(),rule.productId(),rule.serial(),rule.hash())));
        if (!actual.equals(desired.keySet())) throw new IllegalStateException("USBGuard reconcile verification failed");
    }
    public void reconcileLegacy(List<String> legacy) {
        Objects.requireNonNull(legacy, "legacy");
        reconcile(legacy.stream().map(builder::parseLegacy).toList());
    }
    private boolean isManaged(UsbGuardRuleInfo rule) {
        return rule.rawRule() != null && rule.rawRule().contains("label \"" + UsbGuardRuleBuilder.MARKER + "\"");
    }
    private List<UsbGuardRuleInfo> rules() {
        CommandResult result = runner.run("list-rules");
        requireSuccess(result, "list-rules");
        return parser.parseRules(result.output());
    }
    private static void requireSuccess(CommandResult r, String action) {
        if (!r.success()) throw new IllegalStateException("USBGuard " + action + " failed (exit=" + r.exitCode() + "): " + r.output());
    }
}
