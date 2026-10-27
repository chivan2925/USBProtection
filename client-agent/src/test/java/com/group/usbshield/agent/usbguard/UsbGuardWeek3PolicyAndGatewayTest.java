package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsbGuardWeek3PolicyAndGatewayTest {
    private static final String BASE = "1: allow with-interface none-of { 08:*:* }";
    private static final DeviceFingerprint FIRST = new DeviceFingerprint("0951", "1665", null, "firstHash=");
    private static final DeviceFingerprint SECOND = new DeviceFingerprint("0781", "5567", null, "secondHash=");

    static class Runner extends UsbGuardCommandRunner {
        final List<String> rules = new ArrayList<>(List.of(BASE));
        final List<String> mutations = new ArrayList<>();
        int nextId = 2;
        String implicitTarget = "block";
        boolean rejectAppend;
        boolean appendThenFail;
        boolean rejectRemove;
        boolean rejectList;

        @Override
        public CommandResult run(String... args) {
            return switch (args[0]) {
                case "get-parameter" -> new CommandResult(0, implicitTarget);
                case "list-rules" -> rejectList ? new CommandResult(1, "IPC unavailable")
                        : new CommandResult(0, String.join("\n", rules));
                case "append-rule" -> {
                    mutations.add("append");
                    if (rejectAppend) yield new CommandResult(1, "IPC denied");
                    rules.add(nextId++ + ": " + args[1]);
                    yield appendThenFail ? new CommandResult(1, "reply lost") : new CommandResult(0, "added");
                }
                case "remove-rule" -> {
                    mutations.add("remove");
                    if (rejectRemove) yield new CommandResult(1, "IPC denied");
                    rules.removeIf(rule -> rule.startsWith(args[1] + ":"));
                    yield new CommandResult(0, "removed");
                }
                default -> new CommandResult(1, "unsupported");
            };
        }
    }

    private static UsbGuardPolicySynchronizer synchronizer(Runner runner) {
        return new UsbGuardPolicySynchronizer(runner, new UsbGuardOutputParser(), new UsbGuardRuleBuilder());
    }

    @Test
    void idempotentReconciliationPreservesBaseAndUnrelatedRules() {
        Runner runner = new Runner();
        runner.rules.add("2: allow id 1234:abcd serial \"EXTERNAL\"");
        runner.nextId = 3;
        UsbGuardPolicySynchronizer synchronizer = synchronizer(runner);
        synchronizer.reconcile(List.of(FIRST));
        synchronizer.reconcile(List.of(FIRST));
        assertEquals(List.of("append"), runner.mutations);
        assertEquals(BASE, runner.rules.getFirst());
        assertTrue(runner.rules.stream().anyMatch(rule -> rule.contains("EXTERNAL")));
        synchronizer.reconcile(List.of());
        synchronizer.reconcile(List.of());
        assertEquals(List.of("append", "remove"), runner.mutations);
        assertEquals(2, runner.rules.size());
    }

    @Test
    void revokePrecedesGrantDuringPolicyReplacement() {
        Runner runner = new Runner();
        UsbGuardPolicySynchronizer synchronizer = synchronizer(runner);
        synchronizer.reconcile(List.of(FIRST));
        runner.mutations.clear();
        synchronizer.reconcile(List.of(SECOND));
        assertEquals(List.of("remove", "append"), runner.mutations);
        assertTrue(runner.rules.stream().noneMatch(rule -> rule.contains("firstHash=")));
        assertTrue(runner.rules.stream().anyMatch(rule -> rule.contains("secondHash=")));
    }

    @Test
    void daemonRestartReappliesDesiredWithoutDuplicateRules() {
        Runner runner = new Runner();
        UsbGuardPolicySynchronizer synchronizer = synchronizer(runner);
        synchronizer.reconcile(List.of(FIRST));
        runner.rules.removeIf(rule -> rule.contains(UsbGuardRuleBuilder.MARKER));
        synchronizer.reconcile(List.of(FIRST));
        assertEquals(2, runner.mutations.stream().filter("append"::equals).count());
        assertEquals(1, runner.rules.stream().filter(rule -> rule.contains(UsbGuardRuleBuilder.MARKER)).count());
    }

    @Test
    void refusesAmbiguousImplicitPolicyWithoutAnyRuleChanges() {
        Runner runner = new Runner();
        runner.implicitTarget = "unblock";
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
        UsbGuardCliGateway gateway = new UsbGuardCliGateway(runner, new UsbGuardOutputParser(), null);
        assertFalse(gateway.isAvailable());
        runner.implicitTarget = "block\n";
        assertTrue(gateway.health().reachable());
    }

    @Test
    void rejectsMissingBaseAndForeignIdentityCollision() {
        Runner runner = new Runner();
        runner.rules.clear();
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of(FIRST)));
        runner.rules.add(BASE);
        runner.rules.add("2: allow id 0951:1665 hash \"firstHash=\"");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void rejectsUnparseableOrDuplicateManagedRulesWithoutMutation() {
        Runner runner = new Runner();
        runner.rules.add("not-a-rule: allow hash \"firstHash=\" label \"usbshield-managed\"");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of()));
        runner.rules.removeLast();
        runner.rules.add("2: allow hash \"firstHash=\" label \"usbshield-managed\"");
        runner.rules.add("3: allow hash \"firstHash=\" label \"usbshield-managed\"");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of()));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void removeFailurePreventsNewGrantsAndAcknowledgement() {
        Runner runner = new Runner();
        UsbGuardPolicySynchronizer synchronizer = synchronizer(runner);
        synchronizer.reconcile(List.of(FIRST));
        runner.rejectRemove = true;
        runner.mutations.clear();
        assertThrows(IllegalStateException.class, () -> synchronizer.reconcile(List.of(SECOND)));
        assertEquals(List.of("remove"), runner.mutations);
        assertTrue(runner.rules.stream().noneMatch(rule -> rule.contains("secondHash=")));
    }

    @Test
    void failedAppendCannotProduceSuccessfulGatewayApply() {
        Runner runner = new Runner();
        runner.rejectAppend = true;
        UsbGuardCliGateway gateway = new UsbGuardCliGateway(runner, new UsbGuardOutputParser(), null);
        assertThrows(IllegalStateException.class, () -> gateway.applyWhitelist(List.of("firstHash=")));
        assertTrue(runner.rules.stream().noneMatch(rule -> rule.contains(UsbGuardRuleBuilder.MARKER)));
    }

    @Test
    void ambiguousAppendReplyRollsBackNewManagedRule() {
        Runner runner = new Runner();
        runner.appendThenFail = true;
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of(FIRST)));
        assertEquals(List.of("append", "remove"), runner.mutations);
        assertEquals(List.of(BASE), runner.rules);
    }

    @Test
    void readFailureDoesNotMutatePolicy() {
        Runner runner = new Runner();
        runner.rejectList = true;
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void runnerRejectsInvalidArgumentsBeforeLaunchingUsbguard() {
        UsbGuardCommandRunner runner = new UsbGuardCommandRunner(Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, runner::run);
        assertThrows(IllegalArgumentException.class, () -> runner.run(" "));
        assertThrows(IllegalArgumentException.class, () -> runner.run("list-rules\0"));
        assertThrows(NullPointerException.class, () -> runner.run((String) null));
    }
}
