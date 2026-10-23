package com.group.usbshield.agent.usbguard;

import java.util.LinkedHashMap;
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
            desired.put(builder.key(fingerprint), fingerprint);
        }

        requireBlockedDefault();
        List<UsbGuardRuleInfo> before = rules();
        requireBaseRule(before);
        Map<String, UsbGuardRuleInfo> current = managedRules(before);
        requireNoForeignConflict(before, desired.keySet(), current.keySet());

        Set<String> previouslyManaged = Set.copyOf(current.keySet());
        try {
            for (Map.Entry<String, UsbGuardRuleInfo> entry : current.entrySet()) {
                if (!desired.containsKey(entry.getKey())) {
                    requireSuccess(runner.run("remove-rule", Long.toString(entry.getValue().ruleId())), "remove-rule");
                }
            }
            for (Map.Entry<String, DeviceFingerprint> entry : desired.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
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
            requireNoForeignConflict(after, desired.keySet(), actual.keySet());
        } catch (RuntimeException failure) {
            if (!previouslyManaged.containsAll(desired.keySet())) {
                try {
                    removeNewManagedRules(previouslyManaged);
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

    private void removeNewManagedRules(Set<String> previouslyManaged) {
        for (Map.Entry<String, UsbGuardRuleInfo> entry : managedRules(rules()).entrySet()) {
            if (!previouslyManaged.contains(entry.getKey())) {
                requireSuccess(runner.run("remove-rule", Long.toString(entry.getValue().ruleId())), "rollback remove-rule");
            }
        }
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
            } catch (IllegalArgumentException ignored) {
                continue;
            }
        }
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
