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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression for slow USBGuard IPC and stale queue occupancy (PR #56 follow-up). */
class CopilotPR57IpcRecoveryConcurrencyTest {
    private static final String HASH = "HashA=";
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardGateway gateway;
    private UsbGuardEventListener listener;

    @BeforeEach void setup() {
        var users = mock(ActiveUserResolver.class);
        var endpoint = mock(EndpointManager.class);
        var system = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        gateway = mock(UsbGuardGateway.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("ubuntu-a");
        when(users.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), users,
                endpoint, system, sender, notifier, gateway,
                () -> { throw new IOException("unit test: no native watch"); }, 0);
    }

    @AfterEach void cleanup() { listener.stopListening(); }

    private void presence(int id, String action) {
        for (String line : List.of("[device] PresenceChanged: id=" + id,
                "event=" + action, "target=block",
                "device_rule=block id 0951:1665 hash \"" + HASH + "\" with-interface 08:06:50")) {
            listener.processWatchLine(line);
        }
    }

    private void applied(int id, String target) {
        for (String line : List.of("[device] PolicyApplied: id=" + id,
                "target_new=" + target,
                "device_rule=" + target + " id 0951:1665 hash \"" + HASH + "\" with-interface 08:06:50")) {
            listener.processWatchLine(line);
        }
    }

    private static UsbDeviceInfo live() {
        return new UsbDeviceInfo(11, "allow", "0951", "1665", "", "", HASH,
                List.of("08:06:50"), true, "allow id 0951:1665 hash \"HashA=\" with-interface 08:06:50");
    }

    private void failedTransition() {
        doThrow(new IllegalStateException("spool unavailable")).doNothing()
                .when(sender).enqueuePayload(argThat(CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
        presence(11, "Insert");
        applied(11, "block");
        applied(11, "allow");
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
    }

    private static boolean isPolicy(UsbEventPayload event) {
        return event != null && "POLICY_APPLIED".equals(event.getEventType());
    }

    @Test void blockedIpcQueryDoesNotHoldWatchMonitorOrEmitRemovedDevicePolicy() throws Exception {
        failedTransition();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.listDevices()).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("IPC timeout");
            return List.of(live()); // stale snapshot: device was removed during lookup
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> recovery = workers.submit(() -> listener.recoverPending(true));
            assertTrue(started.await(2, TimeUnit.SECONDS), "live query did not start");
            Future<?> remove = workers.submit(() -> presence(11, "Remove"));
            // This would time out if recoverPending held the listener monitor.
            remove.get(2, TimeUnit.SECONDS);
            release.countDown();
            recovery.get(2, TimeUnit.SECONDS);
            assertEquals(0, listener.trackedPendingPolicyQueueCount());
            verify(sender, times(1)).enqueuePayload(argThat(
                    CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
            verify(notifier).onUsbRemoved(any(), any());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test void watchResetInvalidatesInFlightSnapshotAndNextQueryRevalidates() throws Exception {
        failedTransition();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.listDevices()).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("IPC timeout");
            return List.of(); // stale view must not prune a device still attached
        }).thenReturn(List.of(live()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstRecovery = worker.submit(() -> listener.recoverPending(true));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            listener.resetWatchState();
            release.countDown();
            firstRecovery.get(2, TimeUnit.SECONDS);
            assertEquals(1, listener.trackedPendingPolicyQueueCount());
            verify(sender, times(1)).enqueuePayload(argThat(
                    CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
            listener.recoverStalePendingDecisions();
            assertEquals(0, listener.trackedPendingPolicyQueueCount());
            verify(gateway, times(2)).listDevices();
            verify(sender, times(2)).enqueuePayload(argThat(
                    CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test void overlappingWatchLossRecoveryDoesNotUseFirstIpcSnapshot() throws Exception {
        failedTransition();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.listDevices()).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("IPC timeout");
            return List.of(); // obsolete first query result
        }).thenReturn(List.of(live()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = worker.submit(() -> listener.recoverPending(true));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            listener.recoverPending(true); // watch drops again; must invalidate query #1
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            assertEquals(1, listener.trackedPendingPolicyQueueCount());
            verify(sender, times(1)).enqueuePayload(argThat(
                    CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
            listener.recoverStalePendingDecisions(); // now safe to retry
            verify(gateway, times(2)).listDevices();
            verify(sender, times(2)).enqueuePayload(argThat(
                    CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test void slowMissingFinalDecisionQueryDoesNotBlockRemoveOrNotifyAfterRemove() throws Exception {
        presence(11, "Insert"); // pending final decision, but no queued POLICY_APPLIED
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.listDevices()).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("IPC timeout");
            return List.of(live());
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> recovery = workers.submit(() -> listener.recoverPending(true));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            workers.submit(() -> presence(11, "Remove")).get(2, TimeUnit.SECONDS);
            release.countDown();
            recovery.get(2, TimeUnit.SECONDS);
            verify(notifier, never()).notifyBlockedUsb(any(), any());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test void failedWatchLossQueryCannotBeBypassedByNewPolicySection() {
        presence(11, "Insert");
        applied(11, "block");
        when(gateway.listDevices())
                .thenThrow(new IllegalStateException("daemon temporarily unavailable"))
                .thenReturn(List.of(live()));
        listener.recoverPending(true); // cannot prove the old observed device is attached
        applied(11, "allow"); // a replay must not flush without verification
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
        verify(sender, never()).enqueuePayload(argThat(
                CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
        listener.recoverStalePendingDecisions(); // verified attachment permits retry
        verify(sender, times(1)).enqueuePayload(argThat(
                CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
    }

    @Test void missedRemoveWithoutPendingPolicyResetsDedupAndCooldown() {
        presence(11, "Insert");
        applied(11, "block"); // final BLOCK succeeded; no pending policy queue
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        when(gateway.listDevices()).thenReturn(List.of());
        listener.recoverPending(true); // missed Remove while watch was disconnected
        verify(notifier).onUsbRemoved(any(), argThat(device ->
                device.runtimeId() == 11 && HASH.equals(device.hash())));
        presence(11, "Insert"); // same ID and identity inside the 1.5-second window
        applied(11, "block");
        verify(sender, times(2)).enqueuePayload(argThat(event ->
                event != null && "CONNECTED".equals(event.getEventType())));
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
    }

    @Test void staleEmptyQueuesArePrunedBeforeGlobalCapacityCheck() throws Exception {
        presence(11, "Insert");
        applied(11, "block");
        Field field = UsbGuardEventListener.class.getDeclaredField("pendingPolicies");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ArrayDeque<?>> queues = (Map<String, ArrayDeque<?>>) field.get(listener);
        for (int i = 0; i < 1024; i++) queues.put("stale:" + i, new ArrayDeque<>());
        applied(11, "allow");
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(
                CopilotPR57IpcRecoveryConcurrencyTest::isPolicy));
    }
}
