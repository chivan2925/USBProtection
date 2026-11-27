package com.group.usbshield.agent.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentRequest {
    private String hostname;
    private String machineId;
    private String ipAddress;
    private String operatingSystem;
    private UUID installationId;
    private UUID requestId;
    @ToString.Exclude
    private String enrollmentToken;
    /** Client-generated credential, durably staged before enrollment begins. */
    @ToString.Exclude
    private String agentToken;
}
