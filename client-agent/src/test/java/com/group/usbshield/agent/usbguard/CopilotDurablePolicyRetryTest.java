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

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real-world USBGuard event order with failures at the durable enqueue boundary. */
class CopilotDurablePolicyRetryTest {
    private static final String DEVICE = "id 0951:1665 serial \"S1\" hash \"HashA=\" with-interface 08:06:50";
    private EventSenderService sender;
    private DesktopNotificationService notifier;
    private UsbGuardEventListener listener;

    @BeforeEach void setUp() {
        var users = mock(ActiveUserResolver.class);
        var endpoint = mock(EndpointManager.class);
        var system = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("lab-a");
        when(users.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), users,
                endpoint, system, sender, notifier);
    }

    private void insert(int id, String target) {
        for (String line : List.of("[device] PresenceChanged: id=" + id, "event=Insert",
                "target=" + target, "device_rule=" + target + " " + DEVICE)) listener.processWatchLine(line);
    }
    private void applied(int id, String target) {
        for (String line : List.of("[device] PolicyApplied: id=" + id, "target_new=" + target,
                "device_rule=" + target + " " + DEVICE)) listener.processWatchLine(line);
    }

    @Test void failedInitialBlockedEnqueueIsRetriedAtUnchangedFinalBlock() {
        doThrow(new IllegalStateException("first spool write failed")).doNothing()
                .when(sender).enqueuePayload(any());
        insert(1, "block");
        applied(1, "block");
        applied(1, "block");
        verify(sender, times(2)).enqueuePayload(any());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test void failedInitialEnqueueIsRetriedBeforeFinalAllowedEvent() {
        doThrow(new IllegalStateException("first spool write failed")).doNothing().doNothing()
                .when(sender).enqueuePayload(any());
        insert(2, "block");
        applied(2, "allow");
        applied(2, "allow");
        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(captor.capture());
        assertEquals(List.of("CONNECTED", "CONNECTED", "POLICY_APPLIED"),
                captor.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        verifyNoInteractions(notifier);
    }

    @Test void failedFinalAllowedEnqueuePreservesPendingAndRetriesExactlyOnce() {
        doNothing().doThrow(new IllegalStateException("final spool write failed")).doNothing()
                .when(sender).enqueuePayload(any());
        insert(3, "block");
        applied(3, "allow");
        applied(3, "allow");
        applied(3, "allow");
        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(captor.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED", "POLICY_APPLIED"),
                captor.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        verify(notifier, never()).notifyBlockedUsb(any(), any());
    }

    @Test void laterPolicyTransitionFailureDoesNotAdvanceTheObservedTarget() {
        insert(4, "block");
        applied(4, "block");
        doThrow(new IllegalStateException("policy spool temporarily unavailable")).doNothing()
                .when(sender).enqueuePayload(argThat(p -> "POLICY_APPLIED".equals(p.getEventType())));
        applied(4, "allow");
        applied(4, "allow");
        applied(4, "allow");
        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(captor.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED", "POLICY_APPLIED"),
                captor.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }
    @Test void scheduledRecoveryRetriesLaterPolicyWithoutSecondUsbGuardWatchEvent() {
        insert(5, "block");
        applied(5, "block");
        doThrow(new IllegalStateException("policy spool temporarily unavailable")).doNothing()
                .when(sender).enqueuePayload(argThat(p -> "POLICY_APPLIED".equals(p.getEventType())));
        applied(5, "allow");
        listener.recoverStalePendingDecisions(); // no replay: pending policy event still retries
        listener.recoverStalePendingDecisions();
        ArgumentCaptor<UsbEventPayload> captured = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(3)).enqueuePayload(captured.capture());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED", "POLICY_APPLIED"),
                captured.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
    }

    @Test void repeatedEffectiveTargetAfterInterveningTransitionsIsNotDeduplicated() {
        insert(6, "block");
        applied(6, "block");
        applied(6, "allow");
        applied(6, "block");
        applied(6, "allow"); // distinct second ALLOW; not same event as the first
        ArgumentCaptor<UsbEventPayload> events = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(4)).enqueuePayload(events.capture());
        assertEquals(List.of("BLOCKED", "ALLOWED", "BLOCKED", "ALLOWED"),
                events.getAllValues().stream().map(UsbEventPayload::getDecision).toList());
    }

}
