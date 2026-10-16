package com.group.usbshield.server.agentapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.event.UsbEventRepository;
import com.group.usbshield.server.device.UsbDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:week1-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AgentHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbEventRepository events;
    @Autowired UsbDeviceRepository devices;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    @Test
    void heartbeatAndMember2EventReachHttpServerAndPersistInH2() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(connection.isValid(2)).isTrue();
        }
        Path sample = Path.of("../docs/agent-contract/sample-usb-event.json");
        if (!Files.exists(sample)) sample = Path.of("docs/agent-contract/sample-usb-event.json");
        assertThat(sample).exists();
        String payload = Files.readString(sample);
        JsonNode source = mapper.readTree(payload);
        String endpointId = source.get("endpointId").asText();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = java.util.UUID.randomUUID().toString();
        endpoints.save(com.group.usbshield.server.endpoint.Endpoint.builder()
                .endpointId(endpointId).hostname(source.get("hostname").asText())
                .agentTokenHash(encoder.encode(token)).build());
        headers.set("X-Endpoint-Id", endpointId);
        headers.set("X-Agent-Token", token);

        var heartbeat = mapper.createObjectNode();
        heartbeat.put("endpointId", endpointId);
        heartbeat.put("hostname", source.get("hostname").asText());
        heartbeat.put("agentVersion", "week1-verification");
        heartbeat.put("currentPolicyVersion", 0);
        var heartbeatResponse = http.postForEntity("/api/agent/heartbeat",
                new HttpEntity<>(heartbeat.toString(), headers), String.class);
        assertThat(heartbeatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(heartbeatResponse.getBody()).get("accepted").asBoolean()).isTrue();
        var endpoint = endpoints.findByEndpointId(endpointId).orElseThrow();
        assertThat(endpoint.getStatus()).isEqualTo("ONLINE");
        assertThat(endpoint.getLastSeen()).isNotNull();

        var eventResponse = http.postForEntity("/api/agent/events",
                new HttpEntity<>(payload, headers), String.class);
        assertThat(eventResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode response = mapper.readTree(eventResponse.getBody());
        assertThat(response.get("success").asBoolean()).isTrue();
        var event = events.findById(response.get("eventId").asLong()).orElseThrow();
        assertThat(event.getEndpointId()).isEqualTo(endpointId);
        assertThat(event.getLinuxUsername()).isEqualTo("usbdev");
        assertThat(event.getLinuxUid()).isEqualTo(1000L);
        assertThat(event.getSessionId()).isEqualTo("21");
        assertThat(event.getDecision()).isEqualTo("ALLOWED");
        assertThat(event.getEventType()).isEqualTo("CONNECTED");
        assertThat(event.getOccurredAt()).isEqualTo(java.time.Instant.parse("2026-09-28T07:10:16Z"));
        var device = devices.findById(event.getUsbDeviceId()).orElseThrow();
        assertThat(device.getDeviceName()).isEqualTo("USB Optical Mouse");
        assertThat(device.getInterfaces()).isEqualTo("03:01:02");
    }
}
