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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorrectnessPhase1ListenerTest {
    private static final String DEVICE = "id 0951:1665 hash \"Phase1Hash=\" with-interface 08:06:50";
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardGateway gateway;
    private UsbGuardEventListener listener;

    @BeforeEach void setUp() {
        var user = mock(ActiveUserResolver.class);
        var endpoint = mock(EndpointManager.class);
        var system = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        gateway = mock(UsbGuardGateway.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("ubuntu-a");
        when(user.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student01", 1000L, "3", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), user, endpoint,
                system, sender, notifier, gateway,
                () -> { throw new IOException("no USBGuard in Windows test"); }, 0);
    }

    private void presence(String event) {
        for (String line : List.of("[device] PresenceChanged: id=11",
                "event=" + event, "target=block", "device_rule=block " + DEVICE)) {
            listener.processWatchLine(line);
        }
    }

    private void applied(String target) {
        for (String line : List.of("[device] PolicyApplied: id=11", "target_new=" + target,
                "device_rule=" + target + " " + DEVICE)) {
            listener.processWatchLine(line);
        }
    }

    private static UsbDeviceInfo liveBlock() {
        return new UsbDeviceInfo(11, "block", "0951", "1665", "", "", "Phase1Hash=",
                List.of("08:06:50"), true, "block " + DEVICE);
    }

    @Test void pendingInsertSurvivesResetAndFailedIpcWithoutFalseBlockedPopup() {
        doThrow(new IllegalStateException("spool full")).doNothing()
                .when(sender).enqueuePayload(argThat(payload -> payload != null
                        && "CONNECTED".equals(payload.getEventType())));
        presence("Insert"); // first CONNECTED enqueue fails
        listener.resetWatchState(); // broken watch; pending insertion is unverified
        when(gateway.listDevices()).thenThrow(new IllegalStateException("daemon unavailable"))
                .thenReturn(List.of(liveBlock()));
        listener.recoverPending(true);
        applied("block");
        verify(notifier, never()).notifyBlockedUsb(any(), any());
        listener.recoverStalePendingDecisions(); // fresh IPC verifies attached BLOCK
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
        var captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(captor.capture());
        assertEquals(captor.getAllValues().get(0).getEventId(), captor.getAllValues().get(1).getEventId());
        assertEquals(captor.getAllValues().get(0).getOccurredAt(), captor.getAllValues().get(1).getOccurredAt());
        listener.stopListening();
    }

    @Test void failedRemoveEnqueueDoesNotSuppressRapidReinsertionAndCanRetryAudit() {
        doThrow(new IllegalStateException("disk full")).doNothing()
                .when(sender).enqueuePayload(argThat(payload -> payload != null
                        && "DISCONNECTED".equals(payload.getEventType())));
        presence("Insert");
        applied("block");
        presence("Remove"); // durable failure leaves removal audit in retry queue
        presence("Insert"); // same runtime ID and same hash in dedup window
        applied("block");
        verify(sender, times(2)).enqueuePayload(argThat(payload -> payload != null
                && "CONNECTED".equals(payload.getEventType())));
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
        listener.recoverStalePendingDecisions();
        var captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(argThat(payload -> payload != null
                && "DISCONNECTED".equals(payload.getEventType())));
        verify(sender, times(4)).enqueuePayload(captor.capture());
        var removals = captor.getAllValues().stream()
                .filter(payload -> "DISCONNECTED".equals(payload.getEventType())).toList();
        assertEquals(2, removals.size());
        assertEquals(removals.get(0).getEventId(), removals.get(1).getEventId());
        assertEquals(removals.get(0).getOccurredAt(), removals.get(1).getOccurredAt());
        listener.stopListening();
    }

    @Test void laterPolicyRetryKeepsObservedTimeIdentityAndInsertionUser() {
        doThrow(new IllegalStateException("spool full")).doNothing()
                .when(sender).enqueuePayload(argThat(payload -> payload != null
                        && "POLICY_APPLIED".equals(payload.getEventType())));
        presence("Insert");
        applied("block");
        applied("allow");
        listener.recoverStalePendingDecisions();
        var captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(captor.capture());
        var policies = captor.getAllValues().stream()
                .filter(payload -> "POLICY_APPLIED".equals(payload.getEventType())).toList();
        assertEquals(2, policies.size());
        assertEquals(policies.get(0).getEventId(), policies.get(1).getEventId());
        assertEquals(policies.get(0).getOccurredAt(), policies.get(1).getOccurredAt());
        assertEquals("student01", policies.get(1).getLinuxUsername());
        listener.stopListening();
    }
}
