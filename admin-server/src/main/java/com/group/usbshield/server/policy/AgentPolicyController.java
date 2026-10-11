package com.group.usbshield.server.policy;

import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.policy.dto.AgentPolicyResponse;
import com.group.usbshield.server.policy.dto.WhitelistedDeviceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/agent/policy")
@RequiredArgsConstructor
@Tag(name = "Agent Policy", description = "Endpoints cho Client Agent đồng bộ danh sách Whitelist theo endpoint")
public class AgentPolicyController {

    private final EndpointRepository endpointRepository;
    private final EndpointWhitelistEntryRepository whitelistEntryRepository;

    @GetMapping
    @Operation(summary = "Lấy chính sách Whitelist cho Endpoint", description = "Trả về policyVersion và danh sách allowedDevices được cấu hình riêng cho endpoint này")
    public ResponseEntity<AgentPolicyResponse> getPolicy(
            @RequestHeader(value = "X-Endpoint-Id", required = false) String headerEndpointId,
            @RequestParam(value = "endpointId", required = false) String paramEndpointId) {

        String effectiveEndpointId = (headerEndpointId != null && !headerEndpointId.isBlank())
                ? headerEndpointId
                : paramEndpointId;

        if (effectiveEndpointId == null || effectiveEndpointId.isBlank()) {
            effectiveEndpointId = "DEFAULT";
        }

        log.info("[POLICY SYNC] Fetching policy for endpoint: '{}'", effectiveEndpointId);

        Endpoint endpoint = endpointRepository.findByEndpointId(effectiveEndpointId)
                .orElse(null);

        Long currentPolicyVersion = (endpoint != null) ? endpoint.getPolicyVersion() : 1L;

        List<EndpointWhitelistEntry> entries = whitelistEntryRepository.findByEndpointIdAndEnabledTrue(effectiveEndpointId);

        List<WhitelistedDeviceDto> allowedDevices = entries.stream()
                .map(entry -> WhitelistedDeviceDto.builder()
                        .fingerprintValue(entry.getFingerprintValue())
                        .fingerprintType(entry.getFingerprintType())
                        .deviceHash(entry.getFingerprintValue())
                        .build())
                .collect(Collectors.toList());

        AgentPolicyResponse response = AgentPolicyResponse.builder()
                .policyVersion(currentPolicyVersion)
                .allowedDevices(allowedDevices)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/ack")
    @Operation(summary = "Xác nhận đã áp dụng Policy thành công", description = "Client Agent gọi để báo cáo appliedPolicyVersion về Admin Server")
    public ResponseEntity<Map<String, Object>> acknowledgePolicy(
            @RequestHeader(value = "X-Endpoint-Id", required = false) String headerEndpointId,
            @RequestBody(required = false) Map<String, Object> body) {

        Object appliedVerObj = (body != null) ? body.get("appliedPolicyVersion") : null;
        log.info("[POLICY ACK] Received acknowledgment from endpoint '{}', appliedVersion: {}",
                headerEndpointId, appliedVerObj);

        if (headerEndpointId != null && appliedVerObj != null) {
            endpointRepository.findByEndpointId(headerEndpointId).ifPresent(endpoint -> {
                try {
                    endpoint.setAppliedPolicyVersion(Long.parseLong(appliedVerObj.toString()));
                    endpointRepository.save(endpoint);
                } catch (NumberFormatException ignored) {}
            });
        }

        return ResponseEntity.ok(Map.of("acknowledged", true));
    }
}
