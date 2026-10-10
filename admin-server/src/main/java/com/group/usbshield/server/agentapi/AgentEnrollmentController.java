package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEnrollmentRequest;
import com.group.usbshield.server.agentapi.dto.AgentEnrollmentResponse;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent/enroll")
@RequiredArgsConstructor
@Tag(name = "Agent Enrollment", description = "Đăng ký Endpoint mới và cấp mã định danh endpointId")
public class AgentEnrollmentController {

    private final EndpointRepository endpointRepository;

    @PostMapping
    @Operation(summary = "Đăng ký Endpoint vào hệ thống", description = "Cấp phát endpointId và agentToken cho máy trạm Ubuntu")
    public ResponseEntity<AgentEnrollmentResponse> enroll(@RequestBody AgentEnrollmentRequest request) {
        log.info("[ENROLLMENT] Endpoint enrollment requested: hostname='{}', machineId='{}', ip='{}'",
                request.getHostname(), request.getMachineId(), request.getIpAddress());

        String machineId = request.getMachineId();
        Endpoint endpoint = null;
        if (machineId != null && !machineId.isBlank()) {
            endpoint = endpointRepository.findByMachineId(machineId).orElse(null);
        }

        if (endpoint == null) {
            String generatedEndpointId = "EP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            endpoint = Endpoint.builder()
                    .endpointId(generatedEndpointId)
                    .hostname(request.getHostname() != null ? request.getHostname() : "ubuntu-client")
                    .machineId(machineId)
                    .ipAddress(request.getIpAddress())
                    .status("ONLINE")
                    .lastSeen(Instant.now())
                    .policyVersion(1L)
                    .appliedPolicyVersion(0L)
                    .build();
            endpoint = endpointRepository.save(endpoint);
            log.info("[ENROLLMENT SUCCESS] Created new endpoint '{}' for machineId '{}'", endpoint.getEndpointId(), machineId);
        } else {
            endpoint.setLastSeen(Instant.now());
            endpoint.setStatus("ONLINE");
            if (request.getIpAddress() != null) endpoint.setIpAddress(request.getIpAddress());
            endpoint = endpointRepository.save(endpoint);
            log.info("[ENROLLMENT RECOGNIZED] Recognized existing endpoint '{}'", endpoint.getEndpointId());
        }

        AgentEnrollmentResponse response = AgentEnrollmentResponse.builder()
                .endpointId(endpoint.getEndpointId())
                .agentToken("token-" + UUID.randomUUID().toString())
                .currentPolicyVersion(endpoint.getPolicyVersion())
                .build();

        return ResponseEntity.ok(response);
    }
}
