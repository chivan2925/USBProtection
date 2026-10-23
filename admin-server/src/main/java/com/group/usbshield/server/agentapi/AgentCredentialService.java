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
        if(id==null || id.isBlank() || id.length()>100 || token==null || token.isBlank() || token.length()>72) throw new ResponseStatusException(UNAUTHORIZED,"Agent credentials required");
        Endpoint e=endpoints.findByEndpointId(id).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED,"Invalid Agent credentials"));
        if(e.getAgentTokenHash()==null || !e.getAgentTokenHash().matches("\\$2[aby]\\$(0[4-9]|1[0-6])\\$[./A-Za-z0-9]{53}") || !encoder.matches(token,e.getAgentTokenHash())) throw new ResponseStatusException(UNAUTHORIZED,"Invalid Agent credentials");
        return e;
    }
    public static void bind(String authenticatedId,String claimedId) {
        if(claimedId!=null && !authenticatedId.equals(claimedId)) throw new ResponseStatusException(FORBIDDEN,"Endpoint identity mismatch");
    }
}
