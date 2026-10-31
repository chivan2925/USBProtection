package com.group.usbshield.agent.usbguard;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UsbGuardPolicySynchronizer {
    private static final String MANAGED_LABEL = "label \"" + UsbGuardRuleBuilder.MARKER + "\"";

    private final UsbGuardCommandRunner runner;
    private final UsbGuardOutputParser parser;
    private final UsbGuardRuleBuilder builder;

    public UsbGuardPolicySynchronizer(UsbGuardCommandRunner runner, UsbGuardOutputParser parser,
            UsbGuardRuleBuilder builder) {
        this.runner = Objects.requireNonNull(runner);
        this.parser = Objects.requireNonNull(parser);
        this.builder = Objects.requireNonNull(builder);
    }

    public synchronized void reconcile(List<DeviceFingerprint> desiredWhitelist) {
        Objects.requireNonNull(desiredWhitelist, "desiredWhitelist");
        Map<String, DeviceFingerprint> desired = new LinkedHashMap<>();
        for (DeviceFingerprint fingerprint : desiredWhitelist) {
            String key = builder.key(fingerprint);
            DeviceFingerprint existing = desired.putIfAbsent(key, fingerprint);
            if (existing != null && !builder.allowRule(existing).equals(builder.allowRule(fingerprint))) {
                throw new IllegalArgumentException("Conflicting whitelist definitions for identity: " + key);
            }
        }

        requireBlockedDefault();
        List<UsbGuardRuleInfo> before = rules();
        requireBaseRule(before);
        Map<String, UsbGuardRuleInfo> current = managedRules(before);
        requireNoForeignConflict(before, desired.keySet(), current.keySet());
        requireSafeRuleOrder(before, desired);

        Set<String> appendAttempted = new LinkedHashSet<>();
        try {
            for (Map.Entry<String, UsbGuardRuleInfo> entry : current.entrySet()) {
                DeviceFingerprint wanted = desired.get(entry.getKey());
                if (wanted == null || !matchesDefinition(entry.getValue(), wanted)) {
                    requireSuccess(runner.run("remove-rule", Long.toString(entry.getValue().ruleId())), "remove-rule");
                }
            }
            for (Map.Entry<String, DeviceFingerprint> entry : desired.entrySet()) {
                UsbGuardRuleInfo present = current.get(entry.getKey());
                if (present == null || !matchesDefinition(present, entry.getValue())) {
                    appendAttempted.add(entry.getKey());
                    requireSuccess(runner.run("append-rule", builder.allowRule(entry.getValue())), "append-rule");
                }
            }

            requireBlockedDefault();
            List<UsbGuardRuleInfo> after = rules();
            requireBaseRule(after);
            Map<String, UsbGuardRuleInfo> actual = managedRules(after);
            if (!actual.keySet().equals(desired.keySet())) {
                throw new IllegalStateException("USBGuard reconcile verification failed");
            }
            for (Map.Entry<String, DeviceFingerprint> entry : desired.entrySet()) {
                if (!matchesDefinition(actual.get(entry.getKey()), entry.getValue())) {
                    throw new IllegalStateException("USBGuard rule differs from desired whitelist: " + entry.getKey());
                }
            }
            requireNoForeignConflict(after, desired.keySet(), actual.keySet());
            requireSafeRuleOrder(after, desired);
        } catch (RuntimeException failure) {
            if (!appendAttempted.isEmpty()) {
                try {
                    removeAttemptedManagedRules(appendAttempted);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public void reconcileLegacy(List<String> legacy) {
        Objects.requireNonNull(legacy, "legacy");
        reconcile(legacy.stream().map(builder::parseLegacy).toList());
    }

    private void removeAttemptedManagedRules(Set<String> attemptedKeys) {
        for (Map.Entry<String, UsbGuardRuleInfo> entry : managedRules(rules()).entrySet()) {
            if (attemptedKeys.contains(entry.getKey())) {
                requireSuccess(runner.run("remove-rule", Long.toString(entry.getValue().ruleId())), "rollback remove-rule");
            }
        }
    }

    private boolean matchesDefinition(UsbGuardRuleInfo current, DeviceFingerprint desired) {
        String raw = current.rawRule();
        if (raw == null || !raw.contains(":")) {
            return false;
        }
        String actualDefinition = raw.substring(raw.indexOf(':') + 1).strip().replaceAll("\\s+", " ");
        String desiredDefinition = builder.allowRule(desired).replaceAll("\\s+", " ");
        return actualDefinition.equals(desiredDefinition);
    }

    private void requireBlockedDefault() {
        CommandResult result = runner.run("get-parameter", "ImplicitPolicyTarget");
        requireSuccess(result, "get-parameter");
        if (!"block".equals(result.output().strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("ImplicitPolicyTarget must be exactly block; refusing policy changes");
        }
    }

    private void requireBaseRule(List<UsbGuardRuleInfo> rules) {
        if (rules.stream().noneMatch(UsbGuardRuleInfo::isBaseNonStorageRule)) {
            throw new IllegalStateException("USBGuard base non-storage rule missing; refusing policy changes");
        }
    }

    private Map<String, UsbGuardRuleInfo> managedRules(List<UsbGuardRuleInfo> rules) {
        Map<String, UsbGuardRuleInfo> managed = new LinkedHashMap<>();
        for (UsbGuardRuleInfo rule : rules) {
            if (!isManaged(rule)) {
                continue;
            }
            if (!"allow".equalsIgnoreCase(rule.target())) {
                throw new IllegalStateException("Unexpected USBShield-managed rule target");
            }
            String key = builder.key(new DeviceFingerprint(rule.vendorId(), rule.productId(), rule.serial(), rule.hash()));
            if (managed.putIfAbsent(key, rule) != null) {
                throw new IllegalStateException("Duplicate managed USBGuard rule; manual cleanup required");
            }
        }
        return managed;
    }

    private void requireNoForeignConflict(List<UsbGuardRuleInfo> rules, Set<String> desired,
            Set<String> currentManaged) {
        for (UsbGuardRuleInfo rule : rules) {
            if (isManaged(rule) || rule.isBaseNonStorageRule() || !"allow".equalsIgnoreCase(rule.target())) {
                continue;
            }
            if ((rule.hash() == null || rule.hash().isBlank())
                    && (rule.serial() == null || rule.serial().isBlank())) {
                throw new IllegalStateException("Unscoped foreign USBGuard allow rule prevents fail-closed verification: "
                        + rule.ruleId());
            }
            try {
                String key = builder.key(new DeviceFingerprint(rule.vendorId(), rule.productId(), rule.serial(), rule.hash()));
                if (desired.contains(key) || currentManaged.contains(key)) {
                    throw new IllegalStateException("Unmanaged rule conflicts with USBShield whitelist: " + key);
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Unverifiable foreign USBGuard allow rule: " + rule.ruleId(), invalid);
            }
        }
    }

    private void requireSafeRuleOrder(List<UsbGuardRuleInfo> rules, Map<String, DeviceFingerprint> desired) {
        int baseIndex = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).isBaseNonStorageRule()) {
                baseIndex = i;
                break;
            }
        }
        if (baseIndex < 0) {
            throw new IllegalStateException("USBGuard base non-storage rule missing");
        }
        for (int i = 0; i < baseIndex; i++) {
            if (isDeny(rules.get(i))) {
                throw new IllegalStateException("USBGuard deny rule precedes base non-storage allow rule: "
                        + rules.get(i).ruleId());
            }
        }
        for (Map.Entry<String, DeviceFingerprint> entry : desired.entrySet()) {
            int grantIndex = rules.size();
            for (int i = 0; i < rules.size(); i++) {
                UsbGuardRuleInfo candidate = rules.get(i);
                if (isManaged(candidate)
                        && entry.getKey().equals(builder.key(new DeviceFingerprint(
                                candidate.vendorId(), candidate.productId(), candidate.serial(), candidate.hash())))
                        && matchesDefinition(candidate, entry.getValue())) {
                    grantIndex = i;
                    break;
                }
            }
            for (int i = 0; i < grantIndex; i++) {
                UsbGuardRuleInfo candidate = rules.get(i);
                if (isDeny(candidate) && mightOverlap(candidate, entry.getValue())) {
                    throw new IllegalStateException("USBGuard deny rule may precede desired grant: "
                            + candidate.ruleId());
                }
            }
        }
    }

    private static boolean isDeny(UsbGuardRuleInfo rule) {
        return "block".equalsIgnoreCase(rule.target()) || "reject".equalsIgnoreCase(rule.target());
    }

    private static boolean mightOverlap(UsbGuardRuleInfo rule, DeviceFingerprint wanted) {
        if (hasValue(rule.vendorId()) && hasValue(rule.productId())
                && hasValue(wanted.vendorId()) && hasValue(wanted.productId())
                && (!rule.vendorId().equalsIgnoreCase(wanted.vendorId())
                        || !rule.productId().equalsIgnoreCase(wanted.productId()))) {
            return false;
        }
        if (hasValue(rule.hash()) && hasValue(wanted.hash()) && !rule.hash().equals(wanted.hash())) {
            return false;
        }
        if (hasValue(rule.serial()) && hasValue(wanted.serial()) && !rule.serial().equals(wanted.serial())) {
            return false;
        }
        return true;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isManaged(UsbGuardRuleInfo rule) {
        return rule.rawRule() != null && rule.rawRule().contains(MANAGED_LABEL);
    }

    private List<UsbGuardRuleInfo> rules() {
        CommandResult result = runner.run("list-rules");
        requireSuccess(result, "list-rules");
        List<UsbGuardRuleInfo> parsed = parser.parseRules(result.output());
        long rawManaged = result.output().lines().filter(line -> line.contains(MANAGED_LABEL)).count();
        long parsedManaged = parsed.stream().filter(this::isManaged).count();
        if (rawManaged != parsedManaged) {
            throw new IllegalStateException("Unparseable USBShield-managed rule; refusing policy changes");
        }
        long rawRuleHeaders = result.output().lines()
                .filter(line -> line.matches("(?i)^\\s*\\d+:\\s*(?:allow|block|reject)(?:\\s|$).*"))
                .count();
        if (rawRuleHeaders != parsed.size()) {
            throw new IllegalStateException("Unparseable USBGuard rule; refusing policy changes");
        }
        return parsed;
    }

    private static void requireSuccess(CommandResult result, String action) {
        if (result == null || !result.success()) {
            String details = result == null ? "no response" : "exit=" + result.exitCode() + ": " + result.output();
            throw new IllegalStateException("USBGuard " + action + " failed (" + details + ")");
        }
        if (result.output() == null) {
            throw new IllegalStateException("USBGuard " + action + " returned no output");
        }
    }
}
