package com.group.usbshield.agent.endpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointRegistration {
    private String endpointId;
    private String agentToken;
    private Long currentPolicyVersion;
}
