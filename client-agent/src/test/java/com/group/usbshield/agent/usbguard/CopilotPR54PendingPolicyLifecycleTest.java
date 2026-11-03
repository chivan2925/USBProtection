package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression coverage for the two medium findings on PR #54. */
class CopilotPR54PendingPolicyLifecycleTest {
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardEventListener listener;

    @BeforeEach
    void setUp() {
        var users = mock(ActiveUserResolver.class);
        var endpoint = mock(EndpointManager.class);
        var system = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("ubuntu-a");
        when(users.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), users,
                endpoint, system, sender, notifier);
    }

    @AfterEach
    void tearDown() { listener.stopListening(); }

    private void presence(int id, String action, String target, String hash) {
        for (String line : List.of("[device] PresenceChanged: id=" + id, "event=" + action,
                "target=" + target, "device_rule=" + target + " id 0951:1665 hash \"" + hash
                        + "\" with-interface 08:06:50")) listener.processWatchLine(line);
    }

    private void applied(int id, String target, String hash) {
        for (String line : List.of("[device] PolicyApplied: id=" + id, "target_new=" + target,
                "device_rule=" + target + " id 0951:1665 hash \"" + hash
                        + "\" with-interface 08:06:50")) listener.processWatchLine(line);
    }

    private static boolean policyApplied(UsbEventPayload event) {
        return event != null && "POLICY_APPLIED".equals(event.getEventType());
    }

    @Test
    void removedDeviceDropsFailedPolicyTransitionBeforeScheduledRetry() {
        doThrow(new IllegalStateException("spool unavailable"))
                .when(sender).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        presence(11, "Insert", "block", "HashA=");
        applied(11, "block", "HashA=");
        // The confirmed initial BLOCK legitimately produces one notification.
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
        applied(11, "allow", "HashA="); // first attempt fails, remains queued
        assertEquals(1, listener.trackedPendingPolicyQueueCount());

        presence(11, "Remove", "allow", "HashA=");
        listener.recoverStalePendingDecisions();
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        // Removal/recovery must not generate an additional, stale BLOCK popup.
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test
    void reusedRuntimeIdWithDifferentFingerprintDiscardsOldQueuedDecision() {
        doThrow(new IllegalStateException("spool unavailable"))
                .when(sender).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        presence(12, "Insert", "block", "OldHash=");
        applied(12, "block", "OldHash=");
        applied(12, "allow", "OldHash="); // failure for device A
        assertEquals(1, listener.trackedPendingPolicyQueueCount());

        // USBGuard can reuse a runtime ID after a missed Remove.
        presence(12, "Insert", "block", "NewHash=");
        applied(12, "block", "NewHash=");
        listener.recoverStalePendingDecisions();
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingObservationMustBeCheckedBeforeEnqueueingStaleTransition() throws Exception {
        doThrow(new IllegalStateException("spool unavailable"))
                .when(sender).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        presence(13, "Insert", "block", "HashA=");
        applied(13, "block", "HashA=");
        applied(13, "allow", "HashA=");
        assertEquals(1, listener.trackedPendingPolicyQueueCount());

        // Simulates a lost observation (e.g. bounded-state eviction) without
        // relying on a Remove callback to discard the policy queue.
        Field field = UsbGuardEventListener.class.getDeclaredField("observed");
        field.setAccessible(true);
        ((Map<Integer, ?>) field.get(listener)).remove(13);
        listener.recoverStalePendingDecisions();
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyStaleQueuesArePrunedBeforeAdmittingNewPolicyTransition() throws Exception {
        presence(21, "Insert", "block", "NewHash=");
        applied(21, "block", "NewHash=");

        // These seeded empty entries are stale, not active pending audits.
        // The listener is expected to prune them before enforcing the bound.
        Field field = UsbGuardEventListener.class.getDeclaredField("pendingPolicies");
        field.setAccessible(true);
        Map<String, ArrayDeque<?>> pending = (Map<String, ArrayDeque<?>>) field.get(listener);
        for (int i = 0; i < 1024; i++) pending.put("stale-" + i, new ArrayDeque<>());

        applied(21, "allow", "NewHash=");
        assertEquals(0, listener.trackedPendingPolicyQueueCount(),
                "Empty stale queues must be removed and the accepted transition drained");
        verify(sender, times(1)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullValidPendingPolicyMapRejectsNewKeyWithoutCreatingAnEmptyQueue() throws Exception {
        // Obtain an actual pending audit for an observed device by failing its
        // first durable enqueue. Copies remain attached to the same observation
        // so they cannot be pruned as stale merely to make room.
        doThrow(new IllegalStateException("spool unavailable"))
                .when(sender).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        presence(21, "Insert", "block", "HashA=");
        applied(21, "block", "HashA=");
        applied(21, "allow", "HashA=");

        Field field = UsbGuardEventListener.class.getDeclaredField("pendingPolicies");
        field.setAccessible(true);
        Map<String, ArrayDeque<?>> pending = (Map<String, ArrayDeque<?>>) field.get(listener);
        assertEquals(1, pending.size());
        ArrayDeque<?> actualPending = pending.values().iterator().next();
        for (int i = 1; i < 1024; i++) {
            pending.put("synthetic-attached-" + i, new ArrayDeque<>(actualPending));
        }
        assertEquals(1024, listener.trackedPendingPolicyQueueCount());

        presence(22, "Insert", "block", "HashB=");
        applied(22, "block", "HashB=");
        applied(22, "allow", "HashB="); // a new key must be rejected at capacity
        assertEquals(1024, listener.trackedPendingPolicyQueueCount(),
                "A rejected new key must not allocate an empty queue");
        verify(sender, times(1)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
    }

    @Test
    void attachedDeviceStillRetriesUntilPolicyEventIsDurable() {
        doThrow(new IllegalStateException("temporary spool failure")).doNothing()
                .when(sender).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        presence(31, "Insert", "block", "HashA=");
        applied(31, "block", "HashA=");
        applied(31, "allow", "HashA=");
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
        listener.recoverStalePendingDecisions();
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(2)).enqueuePayload(argThat(CopilotPR54PendingPolicyLifecycleTest::policyApplied));
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }
}
