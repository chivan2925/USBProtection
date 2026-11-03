package com.group.usbshield.server.agentapi;
import com.group.usbshield.server.agentapi.dto.*;
import com.group.usbshield.server.endpoint.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/agent/enroll") @RequiredArgsConstructor
public class AgentEnrollmentController {
    private final EndpointRepository endpoints;
    private final PasswordEncoder encoder;
    @Value("${usbshield.enrollment-token:}") private String bootstrap;
    private static final Pattern AGENT_TOKEN = Pattern.compile("[0-9a-f]{64}");
    @PostMapping @Transactional
    public AgentEnrollmentResponse enroll(@RequestBody AgentEnrollmentRequest request){
        if(bootstrap.isBlank())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Enrollment not configured");
        String submittedToken=request.getEnrollmentToken();
        if(submittedToken==null || submittedToken.length()>256 || !MessageDigest.isEqual(bootstrap.getBytes(StandardCharsets.UTF_8),submittedToken.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(UNAUTHORIZED,"Invalid enrollment credential");
        if(request.getMachineId()==null || request.getMachineId().isBlank() || request.getMachineId().length()>100 || request.getHostname()==null || request.getHostname().isBlank() || request.getHostname().length()>150 || (request.getIpAddress()!=null && request.getIpAddress().length()>50) || (request.getOperatingSystem()!=null && request.getOperatingSystem().length()>100))throw new ResponseStatusException(BAD_REQUEST,"Invalid enrollment fields");
        // A staged token can be safely replayed after the server commits but the HTTP response is lost.
        // A machineId alone, even with the bootstrap key, must never recover an existing Agent token.
        String candidate = request.getAgentToken();
        if (candidate != null && !AGENT_TOKEN.matcher(candidate).matches())
            throw new ResponseStatusException(BAD_REQUEST, "Invalid staged Agent credential");
        var existing = endpoints.findByMachineId(request.getMachineId());
        if (existing.isPresent()) {
            var endpoint = existing.get();
            if (candidate != null && endpoint.getAgentTokenHash() != null
                    && encoder.matches(candidate, endpoint.getAgentTokenHash())) {
                return AgentEnrollmentResponse.builder().endpointId(endpoint.getEndpointId())
                        .agentToken(candidate).currentPolicyVersion(endpoint.getPolicyVersion()).build();
            }
            throw new ResponseStatusException(CONFLICT, "Machine already enrolled; administrator recovery required");
        }
        // Compatibility for older deployed agents: new agents always supply a staged token.
        String token = candidate == null ? UUID.randomUUID().toString() + UUID.randomUUID() : candidate;
        var e=Endpoint.builder().endpointId("EP-"+UUID.randomUUID()).hostname(request.getHostname()).machineId(request.getMachineId()).ipAddress(request.getIpAddress()).operatingSystem(request.getOperatingSystem()).agentTokenHash(encoder.encode(token)).build();
        endpoints.save(e);
        return AgentEnrollmentResponse.builder().endpointId(e.getEndpointId()).agentToken(token).currentPolicyVersion(e.getPolicyVersion()).build();
    }
}
