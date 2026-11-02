package com.group.usbshield.server.agentapi;
import com.group.usbshield.server.agentapi.dto.*;
import com.group.usbshield.server.endpoint.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
@RestController @RequestMapping("/api/agent/heartbeat") @RequiredArgsConstructor
public class AgentHeartbeatController {
    private final EndpointRepository endpoints;
    @PostMapping @Transactional
    public AgentHeartbeatResponse heartbeat(@Valid @RequestBody AgentHeartbeatRequest request,Principal principal){
        AgentCredentialService.bind(principal.getName(),request.getEndpointId());
        var e=endpoints.lockByEndpointId(principal.getName()).orElseThrow();
        e.setLastSeen(Instant.now());e.setStatus("ONLINE");
        if(request.getHostname()!=null && !request.getHostname().isBlank())e.setHostname(request.getHostname());
        if(request.getIpAddress()!=null && !request.getIpAddress().isBlank())e.setIpAddress(request.getIpAddress());
        if(request.getOperatingSystem()!=null && !request.getOperatingSystem().isBlank())e.setOperatingSystem(request.getOperatingSystem());
        // A new Agent explicitly reports UNKNOWN when there is no active graphical session.
        // Older Agent payloads omit this field: leave their previously stored value untouched.
        if(request.getCurrentUser()!=null)e.setCurrentUser(request.getCurrentUser());
        return AgentHeartbeatResponse.builder().accepted(true).serverTime(Instant.now()).desiredPolicyVersion(e.getPolicyVersion()).serverPolicyVersion(e.getPolicyVersion()).lastAppliedPolicyVersion(e.getAppliedPolicyVersion()).status("OK").build();
    }
}
