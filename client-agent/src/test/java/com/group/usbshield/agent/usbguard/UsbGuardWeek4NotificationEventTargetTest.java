package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.notification.DesktopNotificationService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsbGuardWeek4NotificationEventTargetTest {
    @Test
    void presenceTargetControlsNotificationEvenIfDeviceRuleStateIsStale() {
        ActiveUserResolver resolver = mock(ActiveUserResolver.class);
        EndpointManager endpoint = mock(EndpointManager.class);
        SystemInfoProvider hostname = mock(SystemInfoProvider.class);
        EventSenderService sender = mock(EventSenderService.class);
        DesktopNotificationService notifier = mock(DesktopNotificationService.class);
        when(resolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.resolved("student01", 1001L, "3", "wayland", "seat0"));
        when(endpoint.getEndpointId()).thenReturn("EP-01");
        when(hostname.getHostname()).thenReturn("lab-pc");
        UsbGuardEventListener listener = new UsbGuardEventListener(new UsbGuardEventParser(),
                resolver, endpoint, hostname, sender, notifier);

        listener.processWatchLine("[device] PresenceChanged: id=11");
        listener.processWatchLine("event=Insert");
        listener.processWatchLine("target=block");
        listener.processWatchLine("device_rule=allow id 0951:1665 hash \"hash-A\" with-interface 08:06:50");

        // PresenceChanged may be provisional: only definitive PolicyApplied(BLOCK) can notify.
        verify(notifier, never()).notifyBlockedUsb(any(), any());
        listener.processWatchLine("[device] PolicyApplied: id=11");
        listener.processWatchLine("target_new=block");
        listener.processWatchLine("device_rule=block id 0951:1665 hash \"hash-A\" with-interface 08:06:50");

        ArgumentCaptor<UsbDeviceInfo> device = ArgumentCaptor.forClass(UsbDeviceInfo.class);
        verify(notifier).notifyBlockedUsb(any(), device.capture());
        assertEquals("block", device.getValue().state());
        ArgumentCaptor<UsbEventPayload> wire = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender).enqueuePayload(wire.capture());
        assertEquals("BLOCKED", wire.getValue().getDecision());
    }
}
