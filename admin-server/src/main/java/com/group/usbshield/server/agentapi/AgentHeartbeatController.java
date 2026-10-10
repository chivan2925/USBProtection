package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentHeartbeatRequest;
import com.group.usbshield.server.agentapi.dto.AgentHeartbeatResponse;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/agent/heartbeat")
@RequiredArgsConstructor
@Tag(name = "Agent Heartbeat", description = "Endpoints cho Client Agent gửi tín hiệu định kỳ và kiểm tra cập nhật policy")
public class AgentHeartbeatController {

    private final EndpointRepository endpointRepository;

    @PostMapping
    @Operation(summary = "Tiếp nhận Heartbeat từ Client Agent", description = "Cập nhật lastSeen cho endpoint và thông báo phiên bản policy hiện hành")
    public ResponseEntity<AgentHeartbeatResponse> handleHeartbeat(
            @Valid @RequestBody AgentHeartbeatRequest request,
            @RequestHeader(value = "X-Endpoint-Id", required = false) String headerEndpointId) {

        String effectiveEndpointId = (request.getEndpointId() != null && !request.getEndpointId().isBlank())
                ? request.getEndpointId()
                : headerEndpointId;

        log.info("[HEARTBEAT] Received heartbeat from endpoint: '{}', hostname: '{}', agentVersion: '{}', currentPolicyVersion: {}",
                effectiveEndpointId, request.getHostname(), request.getAgentVersion(), request.getCurrentPolicyVersion());

        Endpoint endpoint = endpointRepository.findByEndpointId(effectiveEndpointId)
                .orElseGet(() -> Endpoint.builder()
                        .endpointId(effectiveEndpointId)
                        .hostname(request.getHostname() != null ? request.getHostname() : "unknown-host")
                        .status("ONLINE")
                        .policyVersion(1L)
                        .appliedPolicyVersion(0L)
                        .build());

        endpoint.setLastSeen(Instant.now());
        endpoint.setStatus("ONLINE");
        if (request.getHostname() != null) {
            endpoint.setHostname(request.getHostname());
        }
        if (request.getCurrentPolicyVersion() != null) {
            endpoint.setAppliedPolicyVersion(request.getCurrentPolicyVersion());
        }
        endpointRepository.save(endpoint);

        AgentHeartbeatResponse response = AgentHeartbeatResponse.builder()
                .accepted(true)
                .serverTime(Instant.now())
                .desiredPolicyVersion(endpoint.getPolicyVersion())
                .serverPolicyVersion(endpoint.getPolicyVersion())
                .lastAppliedPolicyVersion(endpoint.getAppliedPolicyVersion())
                .status("OK")
                .build();

        return ResponseEntity.ok(response);
    }
}
