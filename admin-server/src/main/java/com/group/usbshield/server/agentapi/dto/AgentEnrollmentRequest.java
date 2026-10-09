package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEnrollmentRequest {
    private String hostname;
    private String machineId;
    private String ipAddress;
    private String enrollmentToken;
}
