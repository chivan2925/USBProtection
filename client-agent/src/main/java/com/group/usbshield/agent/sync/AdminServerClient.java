package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class AdminServerClient {

    private final RestClient restClient;
    private final AgentProperties properties;

    public AdminServerClient(AgentProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .build();
    }

    /**
     * Đăng ký Endpoint mới với Admin Server (POST /api/agent/enroll)
     */
    public Optional<EnrollmentResponse> enroll(EnrollmentRequest request) {
        try {
            log.info("Sending enrollment request to {} for host: {}", properties.getServerUrl(), request.getHostname());
            EnrollmentResponse response = restClient.post()
                    .uri("/api/agent/enroll")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EnrollmentResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Enrollment failed (Server may be offline): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gửi Heartbeat định kỳ (POST /api/agent/heartbeat)
     */
    public Optional<HeartbeatResponse> sendHeartbeat(HeartbeatRequest request, String agentToken) {
        try {
            HeartbeatResponse response = restClient.post()
                    .uri("/api/agent/heartbeat")
                    .header("X-Endpoint-Id", request.getEndpointId())
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(HeartbeatResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.debug("Heartbeat failed (Server offline): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Kéo danh sách Whitelist cho chính endpoint này (GET /api/agent/policy)
     */
    public Optional<PolicyResponse> fetchPolicy(String endpointId, String agentToken) {
        try {
            log.info("Fetching latest policy for endpoint {}", endpointId);
            PolicyResponse response = restClient.get()
                    .uri("/api/agent/policy")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PolicyResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Failed to fetch policy: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Xác nhận đã áp dụng phiên bản policy mới thành công (POST /api/agent/policy/ack)
     */
    public boolean acknowledgePolicy(String endpointId, String agentToken, PolicyAckRequest request) {
        try {
            restClient.post()
                    .uri("/api/agent/policy/ack")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Acknowledged policy version {} to server", request.getAppliedPolicyVersion());
            return true;
        } catch (Exception e) {
            log.warn("Failed to acknowledge policy: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gửi sự kiện cắm/rút USB (POST /api/agent/events)
     */
    public boolean sendEvent(String endpointId, String agentToken, UsbEventPayload payload) {
        try {
            restClient.post()
                    .uri("/api/agent/events")
                    .header("X-Endpoint-Id", endpointId)
                    .header("X-Agent-Token", agentToken != null ? agentToken : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully sent USB event {} for device {} to Admin Server", payload.getEventType(), payload.getDevice());
            return true;
        } catch (Exception e) {
            log.warn("Failed to send USB event to server: {}", e.getMessage());
            return false;
        }
    }
}
