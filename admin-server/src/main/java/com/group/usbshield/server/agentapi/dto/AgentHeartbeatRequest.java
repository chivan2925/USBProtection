package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentHeartbeatRequest {

    @NotBlank(message = "endpointId must not be blank")
    private String endpointId;

    private String hostname;

    private String agentVersion;

    private Long currentPolicyVersion;
}
