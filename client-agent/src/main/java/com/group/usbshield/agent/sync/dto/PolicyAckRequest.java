package com.group.usbshield.agent.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAckRequest {
    private Long appliedPolicyVersion;
    @Builder.Default
    private Instant appliedAt = Instant.now();
    @Builder.Default
    private Integer ruleCount = 0;
}
