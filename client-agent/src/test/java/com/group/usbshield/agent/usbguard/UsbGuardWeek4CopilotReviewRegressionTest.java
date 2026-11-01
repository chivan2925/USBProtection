package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsbGuardWeek4CopilotReviewRegressionTest {
    private static final String BASE = "allow with-interface none-of { 08:*:* }";
    private static final DeviceFingerprint FIRST = new DeviceFingerprint("0951", "1665", null, "firstHash=");
    private static final DeviceFingerprint SECOND = new DeviceFingerprint("0781", "5567", null, "secondHash=");
    private static final DeviceFingerprint THIRD = new DeviceFingerprint("090c", "1000", null, "thirdHash=");

    static class RenumberingRunner extends UsbGuardCommandRunner {
        final List<String> definitions = new ArrayList<>(List.of(BASE));
        final List<String> mutations = new ArrayList<>();
        int failAppendNumber = -1;
        int appends;

        @Override
        public CommandResult run(String... args) {
            return switch (args[0]) {
                case "get-parameter" -> new CommandResult(0, "block");
                case "list-rules" -> {
                    List<String> lines = new ArrayList<>();
                    for (int i = 0; i < definitions.size(); i++) {
                        lines.add((i + 1) + ": " + definitions.get(i));
                    }
                    yield new CommandResult(0, String.join("\n", lines));
                }
                case "append-rule" -> {
                    mutations.add("append:" + args[1]);
                    definitions.add(args[1]);
                    appends++;
                    yield appends == failAppendNumber ? new CommandResult(1, "reply lost")
                            : new CommandResult(0, "ok");
                }
                case "remove-rule" -> {
                    int index = Integer.parseInt(args[1]) - 1;
                    mutations.add("remove:" + args[1]);
                    if (index <= 0 || index >= definitions.size()) {
                        yield new CommandResult(1, "invalid rule id");
                    }
                    definitions.remove(index);
                    yield new CommandResult(0, "ok");
                }
                default -> new CommandResult(1, "unsupported command");
            };
        }
    }

    private static UsbGuardPolicySynchronizer sync(RenumberingRunner runner) {
        return new UsbGuardPolicySynchronizer(runner, new UsbGuardOutputParser(), new UsbGuardRuleBuilder());
    }

    @Test
    void removingTwoStaleGrantsReResolvesIDsAfterEveryDeletion() {
        RenumberingRunner runner = new RenumberingRunner();
        UsbGuardRuleBuilder builder = new UsbGuardRuleBuilder();
        runner.definitions.add(builder.allowRule(FIRST));
        runner.definitions.add(builder.allowRule(SECOND));
        runner.definitions.add("allow id 1234:abcd serial \"EXTERNAL\"");
        sync(runner).reconcile(List.of());
        assertEquals(List.of(BASE, "allow id 1234:abcd serial \"EXTERNAL\""), runner.definitions);
        assertEquals(List.of("remove:2", "remove:2"), runner.mutations);
    }

    @Test
    void replacingMultipleDefinitionsUsesLiveRuleIDs() {
        RenumberingRunner runner = new RenumberingRunner();
        UsbGuardRuleBuilder builder = new UsbGuardRuleBuilder();
        runner.definitions.add(builder.allowRule(FIRST));
        runner.definitions.add(builder.allowRule(SECOND));
        sync(runner).reconcile(List.of(THIRD));
        assertEquals(List.of(BASE, builder.allowRule(THIRD)), runner.definitions);
        assertEquals(List.of("remove:2", "remove:2"), runner.mutations.subList(0, 2));
        assertEquals(3, runner.mutations.size());
    }

    @Test
    void rollbackReResolvesIDsForMultipleAttemptedGrants() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.failAppendNumber = 2;
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST, SECOND)));
        assertEquals(List.of(BASE), runner.definitions);
        assertTrue(runner.mutations.get(2).startsWith("remove:"));
        assertTrue(runner.mutations.get(3).startsWith("remove:"));
    }

    @Test
    void rejectsUnknownTrailingForeignTokenBeforeAnyMutation() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.definitions.add("allow id 1234:abcd serial \"EXTERNAL\" unexpected-token");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void rejectsMalformedNumericHeaderTargetBeforeMutation() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.definitions.add("permit id 1234:abcd serial \"EXTERNAL\"");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void rejectsMalformedNumericDenyTargetBeforeMutation() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.definitions.add("deny with-interface all-of { 08:*:* }");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void rejectsMalformedForeignRuleEvenWithRecognizedTarget() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.definitions.add("allow id 1234:abcd serial \"EXTERNAL\" label \"trusted\" extra");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }

    @Test
    void acceptsKnownForeignAttributesWithoutDeletingThem() {
        RenumberingRunner runner = new RenumberingRunner();
        String foreign = "allow id 1234:abcd name \"External USB\" serial \"EXT\" "
                + "hash \"abc123=\" parent-hash \"parent=\" via-port \"1-2\" "
                + "with-interface all-of { 08:06:50 } with-connect-type \"hotplug\" label \"external\"";
        runner.definitions.add(foreign);
        sync(runner).reconcile(List.of(FIRST));
        assertEquals(foreign, runner.definitions.get(1));
        assertTrue(runner.mutations.stream().noneMatch(cmd -> cmd.startsWith("remove:")));
    }

    @Test
    void unsupportedForeignConditionFailsSafelyWithoutMutation() {
        RenumberingRunner runner = new RenumberingRunner();
        runner.definitions.add("allow id 1234:abcd serial \"EXT\" if random");
        assertThrows(IllegalStateException.class, () -> sync(runner).reconcile(List.of(FIRST)));
        assertTrue(runner.mutations.isEmpty());
    }
}
