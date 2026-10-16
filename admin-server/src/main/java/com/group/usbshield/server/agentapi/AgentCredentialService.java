package com.group.usbshield.server.agentapi;
import com.group.usbshield.server.endpoint.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service @RequiredArgsConstructor
public class AgentCredentialService {
    private final EndpointRepository endpoints;
    private final PasswordEncoder encoder;
    public Endpoint authenticate(String id,String token) {
        if(id==null || token==null || token.isBlank()) throw new ResponseStatusException(UNAUTHORIZED,"Agent credentials required");
        Endpoint e=endpoints.findByEndpointId(id).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED,"Invalid Agent credentials"));
        if(e.getAgentTokenHash()==null || !encoder.matches(token,e.getAgentTokenHash())) throw new ResponseStatusException(UNAUTHORIZED,"Invalid Agent credentials");
        return e;
    }
    public static void bind(String authenticatedId,String claimedId) {
        if(claimedId!=null && !authenticatedId.equals(claimedId)) throw new ResponseStatusException(FORBIDDEN,"Endpoint identity mismatch");
    }
}
