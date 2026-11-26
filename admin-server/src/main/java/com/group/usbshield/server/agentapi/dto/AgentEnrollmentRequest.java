package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEnrollmentRequest {
    @NotBlank @Size(max = 150)
    private String hostname;
    @NotBlank @Size(max = 100)
    private String machineId;
    @Size(max = 50)
    private String ipAddress;
    @Size(max = 100)
    private String operatingSystem;
    @NotNull
    private UUID installationId;
    @NotNull
    private UUID requestId;
    @NotBlank @Size(max = 101) @ToString.Exclude
    private String enrollmentToken;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") @ToString.Exclude
    private String agentToken;
}
