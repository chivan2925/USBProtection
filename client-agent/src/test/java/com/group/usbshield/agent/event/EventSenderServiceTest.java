package com.group.usbshield.agent.event;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventSenderServiceTest {

    private EndpointManager endpointManager;
    private ActiveUserResolver activeUserResolver;
    private SystemInfoProvider systemInfoProvider;
    private AdminServerClient adminServerClient;
    private OutboxSpoolService outboxSpoolService;
    private EventSenderService eventSenderService;

    @BeforeEach
    void setUp() {
        endpointManager = mock(EndpointManager.class);
        activeUserResolver = mock(ActiveUserResolver.class);
        systemInfoProvider = mock(SystemInfoProvider.class);
        adminServerClient = mock(AdminServerClient.class);
        outboxSpoolService = mock(OutboxSpoolService.class);

        when(systemInfoProvider.getHostname()).thenReturn("ubuntu-desktop");

        eventSenderService = new EventSenderService(
                endpointManager,
                activeUserResolver,
                systemInfoProvider,
                adminServerClient,
                outboxSpoolService
        );
    }

    @Test
    @DisplayName("emitUsbEvent sends payload directly when server is reachable")
    void emitUsbEventDirectSend() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .build();
        when(endpointManager.getCurrentRegistration()).thenReturn(reg);

        ActiveUserInfo user = ActiveUserInfo.resolved("student01", 1000L, "2", "wayland", "seat0");
        when(activeUserResolver.resolveCurrentActiveUser()).thenReturn(user);

        when(adminServerClient.sendEvent(eq("EP-01"), eq("tok-01"), any(UsbEventPayload.class)))
                .thenReturn(true);

        Map<String, Object> device = Map.of("vendorId", "0951", "productId", "1666");
        eventSenderService.emitUsbEvent(device, "CONNECTED", "BLOCKED");

        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(adminServerClient).sendEvent(eq("EP-01"), eq("tok-01"), captor.capture());
        UsbEventPayload captured = captor.getValue();
        assertEquals("EP-01", captured.getEndpointId());
        assertEquals("student01", captured.getLinuxUsername());
        assertEquals(1000L, captured.getLinuxUid());
        assertEquals("CONNECTED", captured.getEventType());
        assertEquals("BLOCKED", captured.getDecision());

        verify(outboxSpoolService, never()).spool(any());
    }

    @Test
    @DisplayName("emitUsbEvent spools to outbox when server is offline")
    void emitUsbEventSpoolWhenOffline() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .build();
        when(endpointManager.getCurrentRegistration()).thenReturn(reg);

        ActiveUserInfo user = ActiveUserInfo.resolved("student01", 1000L, "2", "wayland", "seat0");
        when(activeUserResolver.resolveCurrentActiveUser()).thenReturn(user);

        // Server returns false (network unreachable/timeout)
        when(adminServerClient.sendEvent(eq("EP-01"), eq("tok-01"), any(UsbEventPayload.class)))
                .thenReturn(false);

        Map<String, Object> device = Map.of("vendorId", "0951", "productId", "1666");
        eventSenderService.emitUsbEvent(device, "CONNECTED", "BLOCKED");

        verify(outboxSpoolService, times(1)).spool(any(UsbEventPayload.class));
    }

    @Test
    @DisplayName("emitUsbEvent sets UNKNOWN when active user is not detected")
    void emitUsbEventUnknownUser() {
        when(endpointManager.getCurrentRegistration()).thenReturn(null);
        when(activeUserResolver.resolveCurrentActiveUser()).thenReturn(null);

        Map<String, Object> device = Map.of("vendorId", "0951", "productId", "1666");
        eventSenderService.emitUsbEvent(device, "CONNECTED", "BLOCKED");

        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(outboxSpoolService).spool(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getLinuxUsername());
        assertNull(captor.getValue().getLinuxUid());
    }
}
