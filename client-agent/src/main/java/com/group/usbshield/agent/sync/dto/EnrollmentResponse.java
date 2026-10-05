package com.group.usbshield.agent.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {
    private String endpointId;
    private String agentToken;
    private Long currentPolicyVersion;
}
