package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEnrollmentRequest;
import com.group.usbshield.server.agentapi.dto.AgentEnrollmentResponse;
import com.group.usbshield.server.auth.AdminIdentity;
import com.group.usbshield.server.auth.WorkspaceAudit;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class EnrollmentService {
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(15);
    private static final Duration RECEIPT_LIFETIME = Duration.ofHours(24);
    private final EndpointRepository endpoints;
    private final EnrollmentTicketRepository tickets;
    private final WorkspaceAudit audit;
    private final EnrollmentRateLimit rateLimit;

    public record Slot(String endpointId, UUID installationId) {}
    public record IssuedTicket(UUID id, Instant expiresAt, String enrollmentToken) {
        @Override public String toString() { return "IssuedTicket[id=" + id + ", expiresAt=" + expiresAt + "]"; }
    }

    @Transactional
    public Slot createSlot(AdminIdentity admin, UUID installationId, String name) {
        try {
            Endpoint endpoint = endpoints.saveAndFlush(Endpoint.builder()
                    .workspaceId(admin.workspaceId()).installationId(installationId)
                    .endpointId("EP-" + UUID.randomUUID()).hostname(name).build());
            audit.record(admin, "ENDPOINT_SLOT_CREATED", endpoint.getEndpointId());
            return new Slot(endpoint.getEndpointId(), installationId);
        } catch (DataIntegrityViolationException conflict) {
            throw new ResponseStatusException(CONFLICT, "Endpoint slot could not be allocated");
        }
    }

    @Transactional
    public IssuedTicket issue(AdminIdentity admin, String endpointId) {
        Endpoint endpoint = adminEndpoint(admin, endpointId);
        if (endpoint.getAgentTokenHash() != null) {
            throw new ResponseStatusException(CONFLICT, "Revoke the active credential before re-enrollment");
        }
        tickets.revokeAll(admin.workspaceId(), endpointId);
        UUID id = UUID.randomUUID();
        String secret = AgentSecrets.issue();
        Instant expiry = Instant.now().plus(TICKET_LIFETIME);
        tickets.save(new EnrollmentTicket(id, admin.workspaceId(), endpointId, AgentSecrets.digest(secret), expiry));
        audit.record(admin, "ENROLLMENT_TICKET_ISSUED", endpointId);
        return new IssuedTicket(id, expiry, id + "." + secret);
    }

    @Transactional
    public void revoke(AdminIdentity admin, String endpointId) {
        Endpoint endpoint = adminEndpoint(admin, endpointId);
        endpoint.setAgentTokenHash(null);
        endpoint.setCredentialGeneration(endpoint.getCredentialGeneration() + 1);
        endpoint.setAppliedPolicyVersion(0L);
        endpoint.setStatus("OFFLINE");
        endpoint.setLastSeen(null);
        endpoint.setUsbGuardReportedStatus(null);
        endpoint.setUsbGuardCheckedAt(null);
        tickets.revokeAll(admin.workspaceId(), endpointId);
        audit.record(admin, "ENDPOINT_CREDENTIAL_REVOKED", endpointId);
    }

    private Endpoint adminEndpoint(AdminIdentity admin, String endpointId) {
        return endpoints.lockByWorkspaceIdAndEndpointId(admin.workspaceId(), endpointId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Endpoint not found"));
    }

    @Transactional
    public AgentEnrollmentResponse claim(AgentEnrollmentRequest request) {
        String token = request.getEnrollmentToken();
        UUID id = parseTicketId(token);
        if (!rateLimit.allow(id)) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Enrollment temporarily limited");
        String secret = token.substring(37);
        var scope = tickets.findScopeById(id).orElseThrow(EnrollmentService::invalid);
        if (!AgentSecrets.matches(scope.getSecretDigest(), secret)) throw invalid();

        // Lock the endpoint before the ticket, as issuance and revocation do.
        Endpoint endpoint = endpoints.lockByWorkspaceIdAndEndpointId(scope.getWorkspaceId(), scope.getEndpointId())
                .orElseThrow(EnrollmentService::invalid);
        EnrollmentTicket ticket = tickets.lock(id, scope.getWorkspaceId(), scope.getEndpointId())
                .orElseThrow(EnrollmentService::invalid);
        if (ticket.isRevoked() || !endpoint.getInstallationId().equals(request.getInstallationId())) throw invalid();

        Instant now = Instant.now();
        if (ticket.getRequestId() != null) {
            if (!ticket.getRequestId().equals(request.getRequestId()) || !now.isBefore(ticket.getReceiptUntil())
                    || !Objects.equals(ticket.getClaimGeneration(), endpoint.getCredentialGeneration())
                    || !AgentSecrets.matches(endpoint.getAgentTokenHash(), request.getAgentToken())
                    || !Objects.equals(endpoint.getMachineId(), request.getMachineId())) throw invalid();
            return receipt(endpoint, request);
        }

        if (!now.isBefore(ticket.getExpiresAt()) || endpoint.getAgentTokenHash() != null
                || (endpoint.getMachineId() != null && !endpoint.getMachineId().equals(request.getMachineId()))) throw invalid();
        long generation = endpoint.getCredentialGeneration() + 1;
        endpoint.setMachineId(request.getMachineId());
        endpoint.setHostname(request.getHostname());
        endpoint.setIpAddress(request.getIpAddress());
        endpoint.setOperatingSystem(request.getOperatingSystem());
        String credentialDigest = AgentSecrets.digest(request.getAgentToken());
        endpoint.setAgentTokenHash(credentialDigest);
        endpoint.setCredentialGeneration(generation);
        ticket.consume(request.getRequestId(), generation, credentialDigest, now.plus(RECEIPT_LIFETIME));
        try {
            endpoints.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw invalid();
        }
        return receipt(endpoint, request);
    }

    private static UUID parseTicketId(String token) {
        if (token == null || token.length() != 101 || token.charAt(36) != '.'
                || !AgentSecrets.valid(token.substring(37))) throw invalid();
        try {
            return UUID.fromString(token.substring(0, 36));
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
    }

    private static AgentEnrollmentResponse receipt(Endpoint endpoint, AgentEnrollmentRequest request) {
        return AgentEnrollmentResponse.builder().endpointId(endpoint.getEndpointId())
                .agentToken(request.getAgentToken()).currentPolicyVersion(0L).build();
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(UNAUTHORIZED, "Invalid enrollment credential");
    }
}
