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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PR #55: a watch outage can conceal a USB removal even when observed still has an entry. */
class UsbGuardWatchLossPolicyRecoveryTest {
    private static final String HASH = "HashA=";
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardGateway gateway;
    private UsbGuardEventListener listener;

    @BeforeEach
    void setUp() {
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
                () -> { throw new IOException("no native watch in unit tests"); }, 0);
    }

    @AfterEach
    void tearDown() { listener.stopListening(); }

    private void presence(int id, String target, String hash) {
        for (String line : List.of("[device] PresenceChanged: id=" + id, "event=Insert",
                "target=" + target, "device_rule=" + target + " id 0951:1665 hash \"" + hash
                        + "\" with-interface 08:06:50")) listener.processWatchLine(line);
    }

    private void applied(int id, String target, String hash) {
        for (String line : List.of("[device] PolicyApplied: id=" + id, "target_new=" + target,
                "device_rule=" + target + " id 0951:1665 hash \"" + hash
                        + "\" with-interface 08:06:50")) listener.processWatchLine(line);
    }

    private UsbDeviceInfo live(int id, String hash) {
        return new UsbDeviceInfo(id, "allow", "0951", "1665", "", "", hash,
                List.of("08:06:50"), true, "allow id 0951:1665 hash \"" + hash + "\" with-interface 08:06:50");
    }

    private static boolean policy(UsbEventPayload payload) {
        return payload != null && "POLICY_APPLIED".equals(payload.getEventType());
    }

    private void queueFailedAllow() {
        doThrow(new IllegalStateException("spool temporarily unavailable")).doNothing()
                .when(sender).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        presence(11, "block", HASH);
        applied(11, "block", HASH);
        applied(11, "allow", HASH); // enqueue failed: transition is still pending
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
    }

    @Test
    void detachedWithoutRemoveDuringWatchLossMustDropQueuedPolicy() {
        queueFailedAllow();
        when(gateway.listDevices()).thenReturn(List.of()); // unplugged while watch was down
        listener.recoverPending(true);
        listener.recoverStalePendingDecisions();
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        verify(gateway).listDevices(); // validation must happen even with no pending Insert
    }

    @Test
    void reusedRuntimeIdWithDifferentFingerprintCannotReleaseOldAuditEvent() {
        queueFailedAllow();
        when(gateway.listDevices()).thenReturn(List.of(live(11, "OtherHash=")));
        listener.recoverPending(true);
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
    }

    @Test
    void stillAttachedAndSameIdentityRetriesAfterWatchLossValidation() {
        queueFailedAllow();
        when(gateway.listDevices()).thenReturn(List.of(live(11, HASH)));
        listener.recoverPending(true);
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(2)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        verify(sender, times(1)).enqueuePayload(argThat(payload ->
                payload != null && "CONNECTED".equals(payload.getEventType())));
    }

    @Test
    void failedLiveQueryDefersRetryUntilNextSuccessfulVerification() {
        queueFailedAllow();
        when(gateway.listDevices())
                .thenThrow(new IllegalStateException("daemon IPC unavailable"))
                .thenReturn(List.of(live(11, HASH)));
        listener.recoverPending(true); // cannot confirm attachment
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        listener.recoverStalePendingDecisions(); // second lookup succeeds
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(2)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
    }

    @Test
    void retryStillFailingMustRevalidateAgainBeforeNextScheduledAttempt() {
        doThrow(new IllegalStateException("first spool failure"))
                .doThrow(new IllegalStateException("second spool failure"))
                .doNothing().when(sender)
                .enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        presence(11, "block", HASH);
        applied(11, "block", HASH);
        applied(11, "allow", HASH); // pending, first failure
        when(gateway.listDevices()).thenReturn(List.of(live(11, HASH)), List.of());
        listener.recoverPending(true); // attached, but second durable write fails
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
        listener.recoverStalePendingDecisions(); // USB disappeared before third retry
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(2)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
        verify(gateway, times(2)).listDevices();
    }

    @Test
    void failedLiveQueryCannotBeBypassedByNewPolicyAppliedSection() {
        queueFailedAllow();
        when(gateway.listDevices()).thenThrow(new IllegalStateException("daemon IPC unavailable"));
        listener.recoverPending(true);
        applied(11, "block", HASH); // watch resumes, but old pending queue remains unverified
        assertEquals(1, listener.trackedPendingPolicyQueueCount());
        verify(sender, times(1)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
    }

    @Test
    void missedRemoveResetsEventDedupAndDesktopNotificationForRapidReinsert() {
        queueFailedAllow(); // initial BLOCK was durably queued and notified
        verify(sender, times(1)).enqueuePayload(argThat(payload ->
                payload != null && "CONNECTED".equals(payload.getEventType())));
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());

        when(gateway.listDevices()).thenReturn(List.of()); // USB detached while watch was unavailable
        listener.recoverPending(true);
        assertEquals(0, listener.trackedPendingPolicyQueueCount());
        verify(notifier, times(1)).onUsbRemoved(any(), argThat(device ->
                device.runtimeId() == 11 && HASH.equals(device.hash())));

        // Same runtime ID, same fingerprint, immediate reinsert inside 1.5 s.
        // Neither CONNECTED nor BLOCK notification may be suppressed.
        presence(11, "block", HASH);
        applied(11, "block", HASH);
        verify(sender, times(2)).enqueuePayload(argThat(payload ->
                payload != null && "CONNECTED".equals(payload.getEventType())));
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
        verify(sender, times(1)).enqueuePayload(argThat(UsbGuardWatchLossPolicyRecoveryTest::policy));
    }

    @Test
    void stillAttachedWatchRecoveryDoesNotResetNotificationCooldown() {
        queueFailedAllow();
        when(gateway.listDevices()).thenReturn(List.of(live(11, HASH)));
        listener.recoverPending(true);
        verify(notifier, never()).onUsbRemoved(any(), any());
        listener.resetWatchState();
        presence(11, "block", HASH); // replay, not a new physical insert
        applied(11, "allow", HASH); // already applied
        verify(sender, times(1)).enqueuePayload(argThat(payload ->
                payload != null && "CONNECTED".equals(payload.getEventType())));
    }
}
