package com.group.usbshield.server;

import com.group.usbshield.server.agentapi.AgentHeartbeatController;
import com.group.usbshield.server.agentapi.dto.AgentHeartbeatRequest;
import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.AdminEndpointController;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.event.UsbEvent;
import com.group.usbshield.server.event.UsbEventRepository;
import com.group.usbshield.server.policy.EndpointPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual H2 entity persistence and the production endpoint/whitelist response DTOs. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ubuntu_e2e_metadata;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "usbshield.enrollment-token=test-only"})
@Transactional
class EndpointMetadataIntegrationTest {
    @Autowired EndpointRepository endpoints;
    @Autowired UsbEventRepository events;
    @Autowired UsbDeviceRepository devices;
    @Autowired EndpointPolicyService policies;
    @Autowired AdminEndpointController adminEndpoints;
    @Autowired AgentHeartbeatController heartbeats;

    @Test
    void heartbeatMetadataIsStoredAndReturnedWithoutLeakingCredentialHash() {
        String endpointId = "EP-" + UUID.randomUUID();
        endpoints.save(Endpoint.builder().endpointId(endpointId).machineId(UUID.randomUUID().toString())
                .hostname("usbshield-lab").ipAddress("127.0.1.1").build());

        heartbeats.heartbeat(AgentHeartbeatRequest.builder().endpointId(endpointId).currentPolicyVersion(1L)
                .ipAddress("192.168.23.133").operatingSystem("Ubuntu 24.04")
                .currentUser("usbdev").build(), () -> endpointId);

        var dto = adminEndpoints.detail(endpointId);
        assertEquals("192.168.23.133", dto.ipAddress());
        assertEquals("Ubuntu 24.04", dto.operatingSystem());
        assertEquals("usbdev", dto.currentUser());
        assertEquals("ONLINE", dto.status());
        // When a graphical session ends, the new Agent uses explicit UNKNOWN, not a stale username.
        heartbeats.heartbeat(AgentHeartbeatRequest.builder().endpointId(endpointId)
                .currentUser("UNKNOWN").build(), () -> endpointId);
        assertEquals("UNKNOWN", adminEndpoints.detail(endpointId).currentUser());
    }

    @Test
    void whitelistIncludesDeviceMetadataAndCreatedAtButRemainsScopedToEndpoint() {
        String endpointA = "EP-" + UUID.randomUUID();
        String endpointB = "EP-" + UUID.randomUUID();
        for (String id : new String[]{endpointA, endpointB}) {
            endpoints.save(Endpoint.builder().endpointId(id).machineId(UUID.randomUUID().toString())
                    .hostname("lab-" + id).build());
        }
        var usb = devices.save(UsbDevice.builder().vendorId("0951").productId("1665")
                .deviceName("DataTraveler 2.0").serialNumber("SERIAL-LAB")
                .deviceHash("stable-hash-" + UUID.randomUUID()).build());

        events.save(UsbEvent.builder().endpointId(endpointA).usbDeviceId(usb.getId())
                .eventType("CONNECTED").decision("BLOCKED").occurredAt(Instant.now()).build());
        var allowed = policies.allow(endpointA, new EndpointPolicyService.AllowRequest(usb.getId()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> policies.allow(endpointB, new EndpointPolicyService.AllowRequest(usb.getId())));
        assertEquals("DataTraveler 2.0", allowed.name());
        assertEquals("0951", allowed.vendorId());
        assertEquals("1665", allowed.productId());
        assertEquals("SERIAL-LAB", allowed.serial());
        assertNotNull(allowed.createdAt());
        assertEquals(1, policies.list(endpointA).size());
        assertTrue(policies.list(endpointB).isEmpty());
        assertEquals(1L, endpoints.findByEndpointId(endpointB).orElseThrow().getPolicyVersion());
    }
}
