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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression from 2026-11-02 Ubuntu 24.04 native USBGuard watch (Kingston 0951:1665). */
class UsbGuardEffectiveAllowTransitionTest {
    private static final String HASH = "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=";
    private static final String DEVICE = "id 0951:1665 serial \"C81F660E8BE8FFA14601FEF6\" name \"DataTraveler 2.0\" hash \""
            + HASH + "\" parent-hash \"Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=\" via-port \"3-2\" with-interface 08:06:50 with-connect-type \"unknown\"";

    @Test
    void transientBlockedInsertFollowedByEffectiveAllowAppendsFinalEventAndCancelsPopup() {
        ActiveUserResolver resolver = mock(ActiveUserResolver.class);
        EndpointManager endpoint = mock(EndpointManager.class);
        SystemInfoProvider system = mock(SystemInfoProvider.class);
        EventSenderService sender = mock(EventSenderService.class);
        DesktopNotificationService notifier = mock(DesktopNotificationService.class);
        when(resolver.resolveCurrentActiveUser()).thenReturn(ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        when(endpoint.getEndpointId()).thenReturn("EP-A");
        when(system.getHostname()).thenReturn("usbshield-lab");
        UsbGuardEventListener listener = new UsbGuardEventListener(
                new UsbGuardEventParser(), resolver, endpoint, system, sender, notifier);

        for (String line : List.of(
                "[device] PresenceChanged: id=16", "event=Insert", "target=block", "device_rule=block " + DEVICE,
                "[device] PolicyChanged: id=16", "target_old=block", "target_new=allow",
                "device_rule=allow " + DEVICE, "rule_id=13",
                "[device] PolicyApplied: id=16", "target_new=allow", "device_rule=allow " + DEVICE, "rule_id=13")) {
            listener.processWatchLine(line);
        }

        ArgumentCaptor<UsbEventPayload> events = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(sender, times(2)).enqueuePayload(events.capture());
        assertEquals(List.of("BLOCKED", "ALLOWED"),
                events.getAllValues().stream().map(UsbEventPayload::getDecision).toList());
        assertEquals(List.of("CONNECTED", "POLICY_APPLIED"),
                events.getAllValues().stream().map(UsbEventPayload::getEventType).toList());
        verify(notifier, never()).notifyBlockedUsb(any(), any()); // effective allow, no false popup
        assertEquals("usbdev", events.getAllValues().get(1).getLinuxUsername());
    }

    @Test
    void blockRemainingBlockedDoesNotInventAllowedDecision() {
        EventSenderService sender = mock(EventSenderService.class);
        DesktopNotificationService notifier = mock(DesktopNotificationService.class);
        ActiveUserResolver resolver = mock(ActiveUserResolver.class);
        when(resolver.resolveCurrentActiveUser()).thenReturn(ActiveUserInfo.resolved("usbdev", 1000L, "4", "wayland", "seat0"));
        UsbGuardEventListener listener = new UsbGuardEventListener(new UsbGuardEventParser(),
                resolver, mock(EndpointManager.class), mock(SystemInfoProvider.class), sender, notifier);
        for (String line : List.of("[device] PresenceChanged: id=17", "event=Insert", "target=block",
                "device_rule=block " + DEVICE, "[device] PolicyApplied: id=17", "target_new=block",
                "device_rule=block " + DEVICE, "rule_id=4294967294")) listener.processWatchLine(line);
        verify(sender, times(1)).enqueuePayload(argThat(e -> "BLOCKED".equals(e.getDecision())));
        verify(notifier, times(1)).notifyBlockedUsb(any(), argThat(d ->
                d.runtimeId() == 17 && "block".equals(d.state())));
    }
}
