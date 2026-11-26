package com.group.usbshield.server.agentapi;
import com.group.usbshield.server.agentapi.dto.*;
import com.group.usbshield.server.endpoint.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.Instant;
@RestController @RequestMapping("/api/agent/heartbeat") @RequiredArgsConstructor
public class AgentHeartbeatController {
    private final AgentCredentialService credentials;
    @PostMapping @Transactional
    public AgentHeartbeatResponse heartbeat(@Valid @RequestBody AgentHeartbeatRequest request,@AuthenticationPrincipal AgentIdentity agent){
        AgentCredentialService.bind(agent.endpointId(),request.getEndpointId());
        var e=credentials.lockCurrent(agent);
        e.setLastSeen(Instant.now());e.setStatus("ONLINE");
        if(request.getHostname()!=null && !request.getHostname().isBlank())e.setHostname(request.getHostname());
        if(request.getIpAddress()!=null && !request.getIpAddress().isBlank())e.setIpAddress(request.getIpAddress());
        if(request.getOperatingSystem()!=null && !request.getOperatingSystem().isBlank())e.setOperatingSystem(request.getOperatingSystem());
        // A new Agent explicitly reports UNKNOWN when there is no active graphical session.
        // Older Agent payloads omit this field: leave their previously stored value untouched.
        if(request.getCurrentUser()!=null)e.setCurrentUser(request.getCurrentUser());
        // Older Agents omit the field; do NOT extend the freshness of a prior report.
        if (request.getUsbGuardStatus() != null) {
            e.setUsbGuardReportedStatus(request.getUsbGuardStatus());
            e.setUsbGuardCheckedAt(Instant.now());
        }
        return AgentHeartbeatResponse.builder().accepted(true).serverTime(Instant.now()).desiredPolicyVersion(e.getPolicyVersion()).serverPolicyVersion(e.getPolicyVersion()).lastAppliedPolicyVersion(e.getAppliedPolicyVersion()).status("OK").build();
    }
}
