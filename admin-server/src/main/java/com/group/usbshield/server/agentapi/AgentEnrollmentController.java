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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/agent/enroll") @RequiredArgsConstructor
public class AgentEnrollmentController {
    private final EndpointRepository endpoints;
    private final PasswordEncoder encoder;
    @Value("${usbshield.enrollment-token:}") private String bootstrap;
    @PostMapping @Transactional
    public AgentEnrollmentResponse enroll(@RequestBody AgentEnrollmentRequest request){
        if(bootstrap.isBlank())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Enrollment not configured");
        if(request.getEnrollmentToken()==null || !MessageDigest.isEqual(bootstrap.getBytes(StandardCharsets.UTF_8),request.getEnrollmentToken().getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(UNAUTHORIZED,"Invalid enrollment credential");
        if(request.getMachineId()==null || request.getMachineId().isBlank() || request.getMachineId().length()>100 || request.getHostname()==null || request.getHostname().isBlank() || request.getHostname().length()>150)throw new ResponseStatusException(BAD_REQUEST,"machineId and hostname required");
        if(endpoints.findByMachineId(request.getMachineId()).isPresent())throw new ResponseStatusException(CONFLICT,"Machine already enrolled; retain existing credentials or request administrator recovery");
        String token=UUID.randomUUID().toString()+UUID.randomUUID();
        var e=Endpoint.builder().endpointId("EP-"+UUID.randomUUID()).hostname(request.getHostname()).machineId(request.getMachineId()).ipAddress(request.getIpAddress()).agentTokenHash(encoder.encode(token)).build();
        endpoints.save(e);
        return AgentEnrollmentResponse.builder().endpointId(e.getEndpointId()).agentToken(token).currentPolicyVersion(e.getPolicyVersion()).build();
    }
}
