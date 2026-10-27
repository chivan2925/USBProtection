package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsbGuardWeek3CloseoutAuditTest {
    private static final String BASE = "1: allow with-interface none-of { 08:*:* }";

    private static final class FakeRunner extends UsbGuardCommandRunner {
        private final List<String> rules = new ArrayList<>(List.of(BASE));
        private int mutations;

        @Override
        public CommandResult run(String... args) {
            return switch (args[0]) {
                case "get-parameter" -> new CommandResult(0, "block");
                case "list-rules" -> new CommandResult(0, String.join("\n", rules));
                case "append-rule", "remove-rule" -> {
                    mutations++;
                    yield new CommandResult(1, "unexpected policy mutation");
                }
                default -> new CommandResult(1, "unrecognized command");
            };
        }
    }

    private static UsbGuardPolicySynchronizer synchronizer(FakeRunner runner) {
        return new UsbGuardPolicySynchronizer(runner, new UsbGuardOutputParser(), new UsbGuardRuleBuilder());
    }

    @Test
    void baseRuleRequiresCanonicalNonStorageExpression() {
        assertTrue(new UsbGuardRuleInfo(1, "allow", "", "", "", "", BASE).isBaseNonStorageRule());
        assertFalse(new UsbGuardRuleInfo(1, "allow", "", "", "", "",
                "1: allow id 1234:abcd label \"none-of 08:*:*\"").isBaseNonStorageRule());
        assertFalse(new UsbGuardRuleInfo(1, "allow", "", "", "", "",
                "1: allow with-interface all-of { 08:*:* }").isBaseNonStorageRule());
    }

    @Test
    void forgedBaseCannotPermitPolicyMutation() {
        FakeRunner runner = new FakeRunner();
        runner.rules.clear();
        runner.rules.add("1: allow id 1234:abcd label \"none-of 08:*:*\"");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of()));
        assertEquals(0, runner.mutations);
    }

    @Test
    void unscopedForeignAllowFailsWithoutDeletingForeignRule() {
        FakeRunner runner = new FakeRunner();
        runner.rules.add("2: allow id 0951:1665");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of()));
        assertEquals(0, runner.mutations);
        assertEquals(2, runner.rules.size());
    }

    @Test
    void broadForeignInterfaceRuleFailsWithoutMutatingPolicy() {
        FakeRunner runner = new FakeRunner();
        runner.rules.add("2: allow with-interface all-of { 08:06:50 }");
        assertThrows(IllegalStateException.class, () -> synchronizer(runner).reconcile(List.of()));
        assertEquals(0, runner.mutations);
    }

    @Test
    void specificForeignRuleIsStillPreserved() {
        FakeRunner runner = new FakeRunner();
        runner.rules.add("2: allow id 1234:abcd serial \"EXTERNAL\"");
        assertDoesNotThrow(() -> synchronizer(runner).reconcile(List.of()));
        assertEquals(0, runner.mutations);
        assertEquals(2, runner.rules.size());
    }

    @Test
    void malformedFirstEventDoesNotKillSubsequentWatchDelivery() {
        ActiveUserResolver resolver = mock(ActiveUserResolver.class);
        EndpointManager endpoint = mock(EndpointManager.class);
        SystemInfoProvider system = mock(SystemInfoProvider.class);
        EventSenderService sender = mock(EventSenderService.class);
        DesktopNotificationService notification = mock(DesktopNotificationService.class);
        when(endpoint.getEndpointId()).thenThrow(new IllegalStateException("enrollment not ready"))
                .thenReturn("EP-1");
        when(system.getHostname()).thenReturn("ubuntu-lab");
        when(resolver.resolveCurrentActiveUser())
                .thenReturn(ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0"));

        UsbGuardEventListener listener = new UsbGuardEventListener(new UsbGuardEventParser(),
                resolver, endpoint, system, sender, notification);
        sendInsert(listener, 11);
        sendInsert(listener, 11);
        verify(sender, times(1)).sendPayload(any());
        verify(notification, times(1)).notifyBlockedUsb(any(), any());
    }

    private static void sendInsert(UsbGuardEventListener listener, int id) {
        listener.processWatchLine("[device] PresenceChanged: id=" + id);
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"testHash=\" with-interface 08:06:50");
    }
}
