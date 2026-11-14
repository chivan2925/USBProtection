package com.group.usbshield.agent.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentRequest {
    private String hostname;
    private String machineId;
    private String ipAddress;
    private String operatingSystem;
    private String enrollmentToken;
    /** Client-generated credential, durably staged before enrollment begins. */
    private String agentToken;
}
