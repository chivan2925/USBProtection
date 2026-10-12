package com.group.usbshield.server.agentapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.server.agentapi.dto.AgentEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Verify Jackson deserializes Member 2 Week 1 PoC Payload strictly")
    void testDeserializeMember2Week1Payload() throws Exception {
        String jsonPayload = """
        {
          "endpointId": "POC-UNENROLLED-LAB-PC-01",
          "hostname": "usbshield-lab",
          "linuxUsername": "usbdev",
          "linuxUid": 1000,
          "sessionId": "2",
          "sessionType": "wayland",
          "seat": "seat0",
          "device": {
            "vendorId": "093a",
            "productId": "2510",
            "serial": "",
            "name": "USB Optical Mouse",
            "hash": "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=",
            "interface": "03:01:02"
          },
          "eventType": "CONNECTED",
          "decision": "BLOCKED",
          "occurredAt": "2026-09-28T14:10:16+07:00"
        }
        """;

        AgentEventRequest request = objectMapper.readValue(jsonPayload, AgentEventRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.getEndpointId()).isEqualTo("POC-UNENROLLED-LAB-PC-01");
        assertThat(request.getHostname()).isEqualTo("usbshield-lab");
        assertThat(request.getLinuxUsername()).isEqualTo("usbdev");
        assertThat(request.getLinuxUid()).isEqualTo(1000L);
        assertThat(request.getSessionId()).isEqualTo("2");
        assertThat(request.getSessionType()).isEqualTo("wayland");
        assertThat(request.getSeat()).isEqualTo("seat0");
        assertThat(request.getEventType()).isEqualTo("CONNECTED");
        assertThat(request.getDecision()).isEqualTo("BLOCKED");
        assertThat(request.getOccurredAt()).isNotNull();

        assertThat(request.getDevice()).isNotNull();
        assertThat(request.getDevice().getVendorId()).isEqualTo("093a");
        assertThat(request.getDevice().getProductId()).isEqualTo("2510");
        assertThat(request.getDevice().getSerial()).isEmpty();
        assertThat(request.getDevice().getName()).isEqualTo("USB Optical Mouse");
        assertThat(request.getDevice().getHash()).isEqualTo("L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=");
        assertThat(request.getDevice().getInterfaceClass()).isEqualTo("03:01:02");
    }

    @Test
    @DisplayName("Verify Jackson deserializes actual sample-usb-event.json from docs")
    void testDeserializeActualSampleFile() throws Exception {
        Path sampleFilePath = Path.of("../docs/agent-contract/sample-usb-event.json");
        if (!Files.exists(sampleFilePath)) {
            sampleFilePath = Path.of("docs/agent-contract/sample-usb-event.json");
        }

        if (Files.exists(sampleFilePath)) {
            String content = Files.readString(sampleFilePath);
            AgentEventRequest request = objectMapper.readValue(content, AgentEventRequest.class);

            assertThat(request).isNotNull();
            assertThat(request.getEndpointId()).isEqualTo("POC-UNENROLLED-LAB-PC-01");
            assertThat(request.getHostname()).isEqualTo("usbshield-lab");
            assertThat(request.getLinuxUsername()).isEqualTo("usbdev");
            assertThat(request.getLinuxUid()).isEqualTo(1000L);
            assertThat(request.getEventType()).isEqualTo("CONNECTED");
            assertThat(request.getDevice()).isNotNull();
            assertThat(request.getDevice().getVendorId()).isEqualTo("093a");
        }
    }

    @Test
    @DisplayName("Active user attribution fallback to UNKNOWN when absent")
    void testActiveUserAttributionFallback() throws Exception {
        String jsonWithoutUser = """
        {
          "endpointId": "LAB-PC-99",
          "eventType": "CONNECTED",
          "decision": "BLOCKED",
          "occurredAt": "2026-09-28T14:10:16Z"
        }
        """;

        AgentEventRequest request = objectMapper.readValue(jsonWithoutUser, AgentEventRequest.class);
        assertThat(request.getLinuxUsername()).isEqualTo("UNKNOWN");
    }
}
