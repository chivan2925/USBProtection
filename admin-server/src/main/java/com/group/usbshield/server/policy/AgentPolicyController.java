package com.group.usbshield.server.policy;
import com.group.usbshield.server.agentapi.AgentCredentialService;
import com.group.usbshield.server.policy.dto.AgentPolicyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
@RestController @RequestMapping("/api/agent/policy") @RequiredArgsConstructor
public class AgentPolicyController {
    private final EndpointPolicyService service;
    public record Ack(@NotNull @Min(0) Long appliedPolicyVersion,Instant appliedAt,@Min(0) Integer ruleCount){}
    @GetMapping public AgentPolicyResponse policy(Principal principal,@RequestParam(required=false) String endpointId){
        AgentCredentialService.bind(principal.getName(),endpointId);return service.policy(principal.getName());
    }
    @PostMapping("/ack") public Map<String,Object> ack(Principal principal,@Valid @RequestBody Ack body){return service.ack(principal.getName(),body.appliedPolicyVersion());}
}
