package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentHeartbeatResponse {

    @Builder.Default
    private boolean accepted = true;

    @Builder.Default
    private Instant serverTime = Instant.now();

    private Long desiredPolicyVersion;

    private Long serverPolicyVersion;

    private Long lastAppliedPolicyVersion;

    @Builder.Default
    private String status = "OK";
}
