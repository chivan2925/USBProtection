package com.group.usbshield.server.policy;

import com.group.usbshield.server.agentapi.AgentCredentialService;
import com.group.usbshield.server.agentapi.AgentIdentity;
import com.group.usbshield.server.policy.dto.AgentPolicyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/policy")
@RequiredArgsConstructor
@Transactional
public class AgentPolicyController {
    private final EndpointPolicyService service;
    private final AgentCredentialService credentials;

    public record Ack(@NotNull @Min(0) Long appliedPolicyVersion, Instant appliedAt, @Min(0) Integer ruleCount) {}

    @GetMapping
    public AgentPolicyResponse policy(@AuthenticationPrincipal AgentIdentity agent,
                                      @RequestParam(required = false) String endpointId) {
        AgentCredentialService.bind(agent.endpointId(), endpointId);
        credentials.lockCurrent(agent);
        return service.policy(agent.workspaceId(), agent.endpointId());
    }

    @PostMapping("/ack")
    public Map<String, Object> ack(@AuthenticationPrincipal AgentIdentity agent, @Valid @RequestBody Ack body) {
        credentials.lockCurrent(agent);
        return service.ack(agent.workspaceId(), agent.endpointId(), body.appliedPolicyVersion());
    }
}
