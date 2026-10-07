package com.group.usbshield.server.agentapi.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEnrollmentResponse {
    private String endpointId;
    private String agentToken;
    private Long currentPolicyVersion;
}
