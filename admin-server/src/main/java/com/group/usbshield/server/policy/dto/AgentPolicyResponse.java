package com.group.usbshield.server.policy.dto;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentPolicyResponse {
    private Long policyVersion;
    private List<WhitelistedDeviceDto> whitelistedDevices;
}
