package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression cases from Ubuntu A native usbguard watch; all simulated in Java. */
class UbuntuFinalDecisionIntegrationRegressionTest {
    private static final String DEVICE = "id 0951:1665 serial \"S1\" hash \"KingstonHash=\" with-interface 08:06:50";
    private ActiveUserResolver resolver;
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardGateway gateway;
    private UsbGuardEventListener listener;

    @BeforeEach void init() {
        resolver = mock(ActiveUserResolver.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        gateway = mock(UsbGuardGateway.class);
        EndpointManager endpoint = mock(EndpointManager.class);
        SystemInfoProvider system = mock(SystemInfoProvider.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("usbshield-a");
        when(resolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), resolver,
                endpoint, system, sender, notifier, gateway,
                () -> { throw new IOException("unit test does not launch native watch"); }, 0);
    }

    private void insert(int id, String target) {
        for (String line : List.of("[device] PresenceChanged: id=" + id, "event=Insert",
                "target=" + target, "device_rule=" + target + " " + DEVICE)) listener.processWatchLine(line);
    }

    private void applied(int id, String target) {
        for (String line : List.of("[device] PolicyApplied: id=" + id, "target_new=" + target,
                "device_rule=" + target + " " + DEVICE)) listener.processWatchLine(line);
    }

    private static UsbDeviceInfo live(int id, String target) {
        return new UsbDeviceInfo(id, target, "0951", "1665", "S1", "DataTraveler",
                "KingstonHash=", List.of("08:06:50"), true, target + " " + DEVICE);
    }

    @Test void orphanPolicyAppliedWithoutObservedInsertionIsNotAnInventedEvent() {
        // Watch can replay PolicyApplied without the corresponding presence event.
        // A runtime ID alone is not sufficient to attribute a USB or generate a popup.
        applied(98, "block");
        verifyNoInteractions(sender, notifier);
    }

    @Test void replayedInsertWithChangedProvisionalTargetIsNotSecondInsertion() {
        insert(96, "block");
        applied(96, "block");
        listener.resetWatchState();
        insert(96, "allow"); // replay of attached device after a policy change
        applied(96, "allow");
        when(gateway.listDevices()).thenReturn(List.of(live(96, "allow")));
        listener.recoverStalePendingDecisions();
        ArgumentCaptor<UsbEventPayload> payloads = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(payloads.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED"),
                payloads.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        assertEquals("ALLOWED", payloads.getAllValues().get(1).getDecision());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test void allowAfterWatchReconnectStillUpdatesAlreadyObservedDevice() {
        insert(97, "block");
        applied(97, "block");
        listener.resetWatchState(); // USB remains attached across watch restart
        applied(97, "allow");
        when(gateway.listDevices()).thenReturn(List.of(live(97, "allow")));
        listener.recoverStalePendingDecisions();
        ArgumentCaptor<UsbEventPayload> payloads = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(payloads.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED"),
                payloads.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        assertEquals("ALLOWED", payloads.getAllValues().get(1).getDecision());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test void orphanPolicyAppliedAfterWatchResetDoesNotReissueBlockNotification() {
        insert(99, "block");
        listener.resetWatchState(); // watch died before a verified final decision
        applied(99, "block");
        verify(sender, times(1)).enqueuePayload(any());
        verify(notifier, never()).notifyBlockedUsb(any(), any());
    }

    @Test void laterAllowAndRevokeWithoutReinsertionProduceDistinctPolicyEvents() {
        insert(15, "block");
        applied(15, "block");
        when(resolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student01", 1001L, "5", "wayland", "seat0"));
        applied(15, "allow"); // device remains attached; must still update Web
        applied(15, "block"); // revoke while attached
        applied(15, "block"); // duplicate policy applied must not inflate events

        ArgumentCaptor<UsbEventPayload> payloads = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(payloads.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED", "POLICY_APPLIED"),
                payloads.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        assertEquals(List.of("BLOCKED", "ALLOWED", "BLOCKED"),
                payloads.getAllValues().stream().map(UsbEventPayload::getDecision).toList());
        assertTrue(payloads.getAllValues().stream().allMatch(p -> "usbdev".equals(p.getLinuxUsername())),
                "F09: changes to policy must retain the user present at insertion");
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
    }

    @Test void lostPolicyAppliedUsesVerifiedLiveStateNotInitialTransientBlock() {
        insert(16, "block");
        when(gateway.listDevices()).thenReturn(List.of(new UsbDeviceInfo(16, "allow", "0951", "1665",
                "S1", "DataTraveler", "KingstonHash=", List.of("08:06:50"), true, DEVICE)));
        listener.recoverPending(true); // the watch disconnected before PolicyApplied arrived
        ArgumentCaptor<UsbEventPayload> payloads = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(payloads.capture());
        assertEquals("POLICY_APPLIED", payloads.getAllValues().get(1).getEventType());
        assertEquals("ALLOWED", payloads.getAllValues().get(1).getDecision());
        verify(notifier, never()).notifyBlockedUsb(any(), any());
    }

    @Test void missingPolicyAppliedAndVerifiedLiveBlockStillNotifies() {
        insert(17, "block");
        when(gateway.listDevices()).thenReturn(List.of(new UsbDeviceInfo(17, "block", "0951", "1665",
                "S1", "DataTraveler", "KingstonHash=", List.of("08:06:50"), true, DEVICE)));
        listener.recoverPending(true);
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
        verify(sender, times(1)).enqueuePayload(any()); // no duplicated CONNECTED event
    }

    @Test void mismatchedDeviceIdentityDoesNotAuthorizeOrNotify() {
        insert(18, "block");
        when(gateway.listDevices()).thenReturn(List.of(new UsbDeviceInfo(18, "block", "0951", "1665",
                "S1", "Another USB", "DIFFERENT-HASH", List.of("08:06:50"), true, DEVICE)));
        listener.recoverPending(true);
        verify(notifier, never()).notifyBlockedUsb(any(), any());
        verify(sender, times(1)).enqueuePayload(any());
    }
}
