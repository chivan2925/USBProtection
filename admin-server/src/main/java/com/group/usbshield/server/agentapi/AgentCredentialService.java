package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class AgentCredentialService {
    private final EndpointRepository endpoints;

    public AgentIdentity authenticate(String id, String token) {
        if (id == null || id.isBlank() || id.length() > 100 || !AgentSecrets.valid(token)) throw invalid();
        Endpoint endpoint = endpoints.findByEndpointId(id).orElseThrow(AgentCredentialService::invalid);
        if (!AgentSecrets.matches(endpoint.getAgentTokenHash(), token)) throw invalid();
        return new AgentIdentity(endpoint.getWorkspaceId(), endpoint.getEndpointId(), endpoint.getCredentialGeneration());
    }

    /** Call inside the transaction modifying or reading Agent-owned state. */
    public Endpoint lockCurrent(AgentIdentity agent) {
        Endpoint endpoint = endpoints.lockByWorkspaceIdAndEndpointId(agent.workspaceId(), agent.endpointId())
                .orElseThrow(AgentCredentialService::invalid);
        if (endpoint.getAgentTokenHash() == null || endpoint.getCredentialGeneration() != agent.generation()) throw invalid();
        return endpoint;
    }

    public static void bind(String authenticatedId, String claimedId) {
        if (claimedId != null && !authenticatedId.equals(claimedId)) {
            throw new ResponseStatusException(FORBIDDEN, "Endpoint identity mismatch");
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(UNAUTHORIZED, "Invalid Agent credentials");
    }
}
