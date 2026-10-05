package com.group.usbshield.agent.usbguard;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsbGuardEventListenerTest {

    private UsbGuardEventParser eventParser;
    private ActiveUserResolver activeUserResolver;
    private EndpointManager endpointManager;
    private SystemInfoProvider systemInfoProvider;
    private EventSenderService eventSenderService;

    private UsbGuardEventListener listener;

    @BeforeEach
    void setUp() {
        eventParser = new UsbGuardEventParser();
        activeUserResolver = mock(ActiveUserResolver.class);
        endpointManager = mock(EndpointManager.class);
        systemInfoProvider = mock(SystemInfoProvider.class);
        eventSenderService = mock(EventSenderService.class);

        when(endpointManager.getEndpointId()).thenReturn("EP-TEST-001");
        when(systemInfoProvider.getHostname()).thenReturn("ubuntu-test-lab");

        when(activeUserResolver.resolveCurrentActiveUser()).thenReturn(
                ActiveUserInfo.builder()
                        .username("student01")
                        .uid(1001L)
                        .sessionId("3")
                        .sessionType("wayland")
                        .seat("seat0")
                        .build()
        );

        listener = new UsbGuardEventListener(
                eventParser,
                activeUserResolver,
                endpointManager,
                systemInfoProvider,
                eventSenderService
        );
    }

    @Test
    @DisplayName("Bắt sự kiện Insert và áp rule cấm Mass Storage -> Bắn UsbEventPayload chuẩn về EventSenderService")
    void testHandleDeviceRuleEvent() {
        // Dòng rule thật từ log của Member 2
        String rawRuleLine = "device_rule=block id 0951:1665 serial \"C81F660E8BE8FFA14601FEF6\" name \"DataTraveler 2.0\" hash \"xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=\" via-port \"3-2\" with-interface 08:06:50";

        listener.handleDeviceRuleEvent(rawRuleLine, "CONNECTED");

        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(eventSenderService, times(1)).sendPayload(captor.capture());

        UsbEventPayload captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("EP-TEST-001", captured.getEndpointId());
        assertEquals("ubuntu-test-lab", captured.getHostname());
        assertEquals("student01", captured.getLinuxUsername());
        assertEquals(1001L, captured.getLinuxUid());
        assertEquals("3", captured.getSessionId());
        assertEquals("wayland", captured.getSessionType());
        assertEquals("seat0", captured.getSeat());
        assertEquals("CONNECTED", captured.getEventType());
        assertEquals("BLOCKED", captured.getDecision());

        // Kiểm tra metadata thiết bị USB
        assertNotNull(captured.getDevice());
        assertEquals("0951", captured.getDevice().get("vendorId"));
        assertEquals("1665", captured.getDevice().get("productId"));
        assertEquals("DataTraveler 2.0", captured.getDevice().get("name"));
        assertEquals("08:06:50", captured.getDevice().get("interface"));
    }
}
