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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsbGuardWeek3WatchRecoveryTest {
    private UsbGuardEventListener listener;
    private ActiveUserResolver resolver;
    private EventSenderService sender;
    private DesktopNotificationService notifier;

    @BeforeEach
    void prepare() {
        resolver = mock(ActiveUserResolver.class);
        EndpointManager endpoint = mock(EndpointManager.class);
        SystemInfoProvider host = mock(SystemInfoProvider.class);
        sender = mock(EventSenderService.class);
        notifier = mock(DesktopNotificationService.class);
        when(endpoint.getEndpointId()).thenReturn("EP-TEST-001");
        when(host.getHostname()).thenReturn("lab-pc");
        when(resolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0"));
        listener = new UsbGuardEventListener(new UsbGuardEventParser(), resolver, endpoint, host, sender, notifier);
    }

    private void watch(String action) {
        listener.processWatchLine("[device] PresenceChanged: id=11");
        listener.processWatchLine("event=" + action);
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"test-hash\" with-interface 08:06:50");
    }

    @Test
    void duplicateWatchSectionsEmitOnlyOnce() {
        watch("Insert");
        watch("Insert");
        verify(sender, times(1)).sendPayload(any());
        verify(notifier, times(1)).notifyBlockedUsb(any(), any());
    }

    @Test
    void removeThenReinsertIsNotDroppedByDeduplication() {
        watch("Insert");
        watch("Remove");
        watch("Insert");
        verify(sender, times(3)).sendPayload(any());
        verify(notifier, times(2)).notifyBlockedUsb(any(), any());
        verify(notifier, times(1)).onUsbRemoved(any(), any());
    }

    @Test
    void partialSectionIsDiscardedAcrossReconnect() {
        listener.processWatchLine("[device] PresenceChanged: id=11");
        listener.processWatchLine("event=Insert");
        listener.resetWatchState();
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"test-hash\" with-interface 08:06:50");
        verifyNoInteractions(sender);
        watch("Insert");
        verify(sender).sendPayload(any());
    }

    @Test
    void unknownSessionPreservesEventWithoutPopup() {
        when(resolver.resolveCurrentActiveUser()).thenReturn(ActiveUserInfo.unknown("logout"));
        watch("Insert");
        ArgumentCaptor<UsbEventPayload> capture = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender).sendPayload(capture.capture());
        assertEquals("UNKNOWN", capture.getValue().getLinuxUsername());
        assertNull(capture.getValue().getLinuxUid());
        verify(notifier, never()).notifyBlockedUsb(any(), any());
    }

    @Test
    void notifierFailureCannotPreventEventTransport() {
        doThrow(new IllegalStateException("no GUI")).when(notifier).notifyBlockedUsb(any(), any());
        watch("Insert");
        verify(sender).sendPayload(any());
    }

    @Test
    void policyChangedAndPolicyAppliedNeverEmitUsbInsertion() {
        listener.processWatchLine("[device] PolicyChanged: id=11");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"test-hash\" with-interface 08:06:50");
        listener.processWatchLine("[device] PolicyApplied: id=11");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"test-hash\" with-interface 08:06:50");
        verifyNoInteractions(sender);
        verifyNoInteractions(notifier);
    }

    @Test
    void oversizedWatchSectionIsDroppedWithoutEvent() {
        listener.processWatchLine("[device] PresenceChanged: id=11");
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("X".repeat(40000));
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"test-hash\" with-interface 08:06:50");
        verifyNoInteractions(sender);
    }
}
