package com.group.usbshield.agent.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AdminServerClientContractTest {

    private AdminServerClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setServerUrl("http://localhost:8080");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.client = new AdminServerClient(properties, builder.build());
        this.objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("enroll() should send enrollment request and parse response")
    void enrollSuccess() throws Exception {
        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-TEST-001")
                .agentToken("token-secret-123")
                .currentPolicyVersion(1L)
                .build();

        mockServer.expect(requestTo("http://localhost:8080/api/agent/enroll"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        EnrollmentRequest request = EnrollmentRequest.builder()
                .hostname("ubuntu-test")
                .machineId("machine-uuid-1")
                .ipAddress("192.168.1.100")
                .enrollmentToken("secret")
                .build();

        Optional<EnrollmentResponse> result = client.enroll(request);
        assertTrue(result.isPresent());
        assertEquals("EP-TEST-001", result.get().getEndpointId());
        assertEquals("token-secret-123", result.get().getAgentToken());
        assertEquals(1L, result.get().getCurrentPolicyVersion());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendHeartbeat() should attach X-Endpoint-Id and X-Agent-Token headers")
    void sendHeartbeatWithAuthHeaders() throws Exception {
        HeartbeatResponse response = HeartbeatResponse.builder()
                .status("ONLINE")
                .serverPolicyVersion(3L)
                .build();

        mockServer.expect(requestTo("http://localhost:8080/api/agent/heartbeat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Endpoint-Id", "EP-TEST-001"))
                .andExpect(header("X-Agent-Token", "token-secret-123"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        HeartbeatRequest request = HeartbeatRequest.builder()
                .endpointId("EP-TEST-001")
                .currentPolicyVersion(2L)
                .build();

        Optional<HeartbeatResponse> result = client.sendHeartbeat(request, "token-secret-123");
        assertTrue(result.isPresent());
        assertEquals(3L, result.get().getServerPolicyVersion());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchPolicy() should deserialize policy and handle allowedDevices alias")
    void fetchPolicyWithAlias() {
        String jsonPayload = """
                {
                  "policyVersion": 5,
                  "allowedDevices": [
                    {
                      "vendorId": "0951",
                      "productId": "1666",
                      "serialNumber": "SN001",
                      "deviceHash": "hash001",
                      "deviceName": "Kingston USB",
                      "fingerprintValue": "id 0951:1666 serial \\"SN001\\""
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8080/api/agent/policy"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Endpoint-Id", "EP-TEST-001"))
                .andExpect(header("X-Agent-Token", "token-secret-123"))
                .andRespond(withSuccess(jsonPayload, MediaType.APPLICATION_JSON));

        Optional<PolicyResponse> result = client.fetchPolicy("EP-TEST-001", "token-secret-123");
        assertTrue(result.isPresent());
        assertEquals(5L, result.get().getPolicyVersion());
        assertEquals(1, result.get().getWhitelistedDevices().size());
        assertEquals("0951", result.get().getWhitelistedDevices().get(0).getVendorId());
        mockServer.verify();
    }

    @Test
    @DisplayName("acknowledgePolicy() should send ACK payload with authentication headers")
    void acknowledgePolicySuccess() {
        mockServer.expect(requestTo("http://localhost:8080/api/agent/policy/ack"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Endpoint-Id", "EP-TEST-001"))
                .andExpect(header("X-Agent-Token", "token-secret-123"))
                .andRespond(withSuccess());

        PolicyAckRequest ack = PolicyAckRequest.builder()
                .appliedPolicyVersion(5L)
                .ruleCount(1)
                .build();

        boolean ok = client.acknowledgePolicy("EP-TEST-001", "token-secret-123", ack);
        assertTrue(ok);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendEvent() should send real USB event payload")
    void sendEventSuccess() {
        mockServer.expect(requestTo("http://localhost:8080/api/agent/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Endpoint-Id", "EP-TEST-001"))
                .andExpect(header("X-Agent-Token", "token-secret-123"))
                .andRespond(withSuccess());

        UsbEventPayload payload = UsbEventPayload.builder()
                .endpointId("EP-TEST-001")
                .hostname("ubuntu-test")
                .linuxUsername("student01")
                .linuxUid(1000L)
                .sessionId("2")
                .sessionType("wayland")
                .seat("seat0")
                .eventType("CONNECTED")
                .decision("BLOCKED")
                .device(Map.of("vendorId", "0951", "productId", "1666"))
                .build();

        boolean ok = client.sendEvent("EP-TEST-001", "token-secret-123", payload);
        assertTrue(ok);
        mockServer.verify();
    }
}
