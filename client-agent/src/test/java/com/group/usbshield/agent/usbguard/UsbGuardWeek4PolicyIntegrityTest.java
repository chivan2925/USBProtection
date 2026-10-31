package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsbGuardWeek4PolicyIntegrityTest {
    private static final String BASE = "1: allow with-interface none-of { 08:*:* }";
    private static final DeviceFingerprint FIRST = new DeviceFingerprint("0951", "1665", null, "sameHash=");
    private static final DeviceFingerprint CHANGED = new DeviceFingerprint("0781", "5567", null, "sameHash=");

    static class Runner extends UsbGuardCommandRunner {
        final List<String> rules = new ArrayList<>(List.of(BASE));
        final List<String> mutations = new ArrayList<>();
        int nextId = 2;
        boolean appendThenFail;

        @Override
        public CommandResult run(String... args) {
            return switch (args[0]) {
                case "get-parameter" -> new CommandResult(0, "block");
                case "list-rules" -> new CommandResult(0, String.join("\n", rules));
                case "append-rule" -> {
                    mutations.add("append");
                    rules.add(nextId++ + ": " + args[1]);
                    yield appendThenFail ? new CommandResult(1, "reply lost") : new CommandResult(0, "ok");
                }
                case "remove-rule" -> {
                    mutations.add("remove");
                    rules.removeIf(rule -> rule.startsWith(args[1] + ":"));
                    yield new CommandResult(0, "ok");
                }
                default -> new CommandResult(1, "unsupported");
            };
        }
    }

    private UsbGuardPolicySynchronizer sync(Runner runner) {
        return new UsbGuardPolicySynchronizer(runner, new UsbGuardOutputParser(), new UsbGuardRuleBuilder());
    }

    @Test
    void vidPidOnlyCannotMasqueradeAsHashOnlyFingerprint() {
        UsbGuardRuleBuilder builder = new UsbGuardRuleBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.parseLegacy("0951:1665"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.allowRule(new DeviceFingerprint(null, null, null, "0951:1665")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.allowRule(new DeviceFingerprint(null, null, null, "====")));
        assertEquals("HASH:Abc+/123=", builder.key(builder.parseLegacy("Abc+/123=")));
    }

    @Test
    void conflictingDefinitionsForSameHashFailBeforeRuleMutation() {
        Runner runner = new Runner();
        assertThrows(IllegalArgumentException.class, () -> sync(runner).reconcile(List.of(FIRST, CHANGED)));
        assertTrue(runner.mutations.isEmpty());
        assertEquals(List.of(BASE), runner.rules);
    }

    @Test
    void sameHashWithChangedVidPidReplacesStaleRuleAndThenIsIdempotent() {
        Runner runner = new Runner();
        UsbGuardPolicySynchronizer synchronizer = sync(runner);
        synchronizer.reconcile(List.of(FIRST));
        runner.mutations.clear();
        synchronizer.reconcile(List.of(CHANGED));
        assertEquals(List.of("remove", "append"), runner.mutations);
        assertEquals(2, runner.rules.size());
        assertTrue(runner.rules.get(1).contains("id 0781:5567"));
        runner.mutations.clear();
        synchronizer.reconcile(List.of(CHANGED));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void managedRuleWithAdditionalConstraintIsReplaced() {
        Runner runner = new Runner();
        runner.rules.add("2: allow id 0951:1665 hash \"sameHash=\" with-interface all-of { 08:06:50 } label \"usbshield-managed\"");
        runner.nextId = 3;
        sync(runner).reconcile(List.of(FIRST));
        assertEquals(List.of("remove", "append"), runner.mutations);
        assertEquals("3: allow id 0951:1665 hash \"sameHash=\" label \"usbshield-managed\"",
                runner.rules.get(1));
    }

    @Test
    void failedAppendDuringRuleReplacementIsNotReportedAsApplied() {
        Runner runner = new Runner();
        UsbGuardPolicySynchronizer synchronizer = sync(runner);
        synchronizer.reconcile(List.of(FIRST));
        runner.mutations.clear();
        runner.appendThenFail = true;
        assertThrows(IllegalStateException.class, () -> synchronizer.reconcile(List.of(CHANGED)));
        assertEquals(List.of("remove", "append", "remove"), runner.mutations);
        assertEquals(List.of(BASE), runner.rules);
    }

    @Test
    void restrictiveBaseRuleCannotImpersonateFullNonStorageAllow() {
        Runner runner = new Runner();
        runner.rules.clear();
        runner.rules.add("1: allow with-interface none-of { 08:*:* } hash \"someHash=\"");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void malformedForeignAllowFailsClosedWithoutDeletingForeignRule() {
        Runner runner = new Runner();
        runner.rules.add("2: allow id 0951:1665 hash \"!!!\"");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertEquals(2, runner.rules.size());
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void foreignSpecificRuleIsPreservedAndBroadRuleFails() {
        Runner runner = new Runner();
        runner.rules.add("2: allow id 1234:abcd serial \"EXTERNAL\"");
        runner.nextId = 3;
        sync(runner).reconcile(List.of(FIRST));
        assertEquals("2: allow id 1234:abcd serial \"EXTERNAL\"", runner.rules.get(1));
        runner.mutations.clear();
        runner.rules.add("4: allow id 0951:1665");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void broadDenyBeforeBaseCouldBlockHidAndFailsWithoutMutation() {
        Runner runner = new Runner();
        runner.rules.add(0, "2: block");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of()));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void overlappingDenyBeforeNewManagedAllowCannotProduceFalseSuccess() {
        Runner runner = new Runner();
        runner.rules.add("2: block with-interface all-of { 08:*:* }");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void targetedUnrelatedDenyDoesNotObstructWhitelistedHash() {
        Runner runner = new Runner();
        runner.rules.add("2: block id 0781:5567 hash \"otherHash=\"");
        runner.nextId = 3;
        sync(runner).reconcile(List.of(FIRST));
        assertEquals(List.of("append"), runner.mutations);
        assertEquals(3, runner.rules.size());
    }

    @Test
    void unparsableNumericRuleIdCannotBeIgnoredDuringVerification() {
        Runner runner = new Runner();
        runner.rules.add("9999999999999999999999999: allow id 0951:1665");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }
}
