package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.IsolatedPostgresDb;
import com.group.usbshield.server.TestPostgresSchemas;
import com.group.usbshield.server.WorkspaceTestData;
import com.group.usbshield.server.agentapi.dto.AgentEnrollmentRequest;
import com.group.usbshield.server.agentapi.dto.AgentHeartbeatRequest;
import com.group.usbshield.server.auth.AdminIdentity;
import com.group.usbshield.server.auth.AdminProvisioningService;
import com.group.usbshield.server.auth.AdminUserRepository;
import com.group.usbshield.server.auth.WorkspaceRepository;
import com.group.usbshield.server.policy.EndpointPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

@IsolatedPostgresDb
@SpringBootTest
class EnrollmentRetryContractTest {
    @DynamicPropertySource
    static void isolatedPostgres(DynamicPropertyRegistry registry) {
        TestPostgresSchemas.register(registry, EnrollmentRetryContractTest.class);
    }

    @Autowired WorkspaceTestData fixture;
    @Autowired AdminUserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AdminProvisioningService admins;
    @Autowired EnrollmentService enrollment;
    @Autowired AgentCredentialService credentials;
    @Autowired AgentHeartbeatController heartbeats;
    @Autowired EndpointPolicyService policies;
    @Autowired JdbcTemplate jdbc;

    @Test void concurrentClaimsAuthorizeOneCandidateAndOnlyItsRetry() throws Exception {
        var admin = fixture.admin();
        var slot = enrollment.createSlot(admin, UUID.randomUUID(), "Synthetic host");
        var ticket = enrollment.issue(admin, slot.endpointId());
        var a = request(slot, ticket);
        var b = request(slot, ticket);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(afterGate(gate, () -> claimStatus(a)));
            var second = pool.submit(afterGate(gate, () -> claimStatus(b)));
            gate.countDown();
            var aStatus = first.get(10, TimeUnit.SECONDS);
            var bStatus = second.get(10, TimeUnit.SECONDS);
            assertTrue((aStatus == OK && bStatus == UNAUTHORIZED) || (bStatus == OK && aStatus == UNAUTHORIZED));
            var winner = aStatus == OK ? a : b;
            var loser = aStatus == OK ? b : a;
            assertEquals(slot.endpointId(), enrollment.claim(winner).getEndpointId());
            assertEquals(winner.getAgentToken(), enrollment.claim(winner).getAgentToken());
            assertEquals(UNAUTHORIZED, claimStatus(loser));
            loser.setRequestId(winner.getRequestId());
            assertEquals(UNAUTHORIZED, claimStatus(loser));
            assertEquals(1, credentials.authenticate(slot.endpointId(), winner.getAgentToken()).generation());
        }
    }

    @Test void oneVerifiedInstallationCannotBeProvisionedInTwoWorkspaces() throws Exception {
        String username = "other-admin";
        admins.create(username, WorkspaceTestData.PASSWORD);
        var user = users.findByUsername(username).orElseThrow();
        var workspace = workspaces.findByOwnerAdminId(user.getId()).orElseThrow();
        var other = new AdminIdentity(user.getId(), workspace.getId(), username);
        var first = fixture.admin();
        var id = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(afterGate(gate, () -> slotStatus(first, id)));
            var b = pool.submit(afterGate(gate, () -> slotStatus(other, id)));
            gate.countDown();
            assertEquals(1, java.util.stream.Stream.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))
                    .filter(CREATED::equals).count());
            assertEquals(1, jdbc.queryForObject("select count(*) from endpoints where installation_id = ?", Long.class, id));
        }
    }

    @Test void invalidInstallationExpiredTicketAndRevokedGenerationCannotClaim() {
        var admin = fixture.admin();
        var slot = enrollment.createSlot(admin, UUID.randomUUID(), "Synthetic host");
        var expired = enrollment.issue(admin, slot.endpointId());
        jdbc.update("update enrollment_tickets set expires_at = now() - interval '1 second' where id = ?", expired.id());
        assertEquals(UNAUTHORIZED, claimStatus(request(slot, expired)));
        var replaced = enrollment.issue(admin, slot.endpointId());
        var valid = enrollment.issue(admin, slot.endpointId());
        assertEquals(UNAUTHORIZED, claimStatus(request(slot, replaced)));
        var a = request(slot, valid);
        a.setInstallationId(UUID.randomUUID());
        assertEquals(UNAUTHORIZED, claimStatus(a));
        a.setInstallationId(slot.installationId());
        assertEquals(OK, claimStatus(a));
        var originalAgent = credentials.authenticate(slot.endpointId(), a.getAgentToken());
        policies.ack(admin.workspaceId(), slot.endpointId(), 1L);
        assertEquals(1L, heartbeatAppliedVersion(originalAgent));
        jdbc.update("update enrollment_tickets set receipt_until = now() - interval '1 second' where id = ?", valid.id());
        assertEquals(UNAUTHORIZED, claimStatus(a));
        assertEquals(1, credentials.authenticate(slot.endpointId(), a.getAgentToken()).generation());
        enrollment.revoke(admin, slot.endpointId());
        assertEquals(UNAUTHORIZED, claimStatus(a));
        assertEquals(UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> credentials.authenticate(slot.endpointId(), a.getAgentToken())).getStatusCode());
        var recovery = request(slot, enrollment.issue(admin, slot.endpointId()));
        recovery.setMachineId(a.getMachineId());
        String replacement = recovery.getAgentToken();
        recovery.setAgentToken(a.getAgentToken());
        assertEquals(UNAUTHORIZED, claimStatus(recovery));
        recovery.setAgentToken(replacement);
        assertEquals(OK, claimStatus(recovery));
        var replacementAgent = credentials.authenticate(slot.endpointId(), recovery.getAgentToken());
        assertEquals(3, replacementAgent.generation());
        assertEquals(0L, heartbeatAppliedVersion(replacementAgent));
        assertEquals(1L, policies.policy(admin.workspaceId(), slot.endpointId()).getPolicyVersion());
        policies.ack(admin.workspaceId(), slot.endpointId(), 1L);
        assertEquals(1L, heartbeatAppliedVersion(replacementAgent));
    }

    private long heartbeatAppliedVersion(AgentIdentity agent) {
        var request = AgentHeartbeatRequest.builder().endpointId(agent.endpointId()).build();
        return heartbeats.heartbeat(request, agent).getLastAppliedPolicyVersion();
    }

    private AgentEnrollmentRequest request(EnrollmentService.Slot slot, EnrollmentService.IssuedTicket ticket) {
        return AgentEnrollmentRequest.builder().installationId(slot.installationId()).requestId(UUID.randomUUID())
                .enrollmentToken(ticket.enrollmentToken()).agentToken(AgentSecrets.issue())
                .hostname("Synthetic host").machineId(UUID.randomUUID().toString()).build();
    }

    private org.springframework.http.HttpStatusCode claimStatus(AgentEnrollmentRequest request) {
        try {
            enrollment.claim(request);
            return OK;
        } catch (ResponseStatusException denied) {
            return denied.getStatusCode();
        }
    }

    private org.springframework.http.HttpStatusCode slotStatus(AdminIdentity admin, UUID installationId) {
        try {
            enrollment.createSlot(admin, installationId, "Synthetic host");
            return CREATED;
        } catch (ResponseStatusException denied) {
            assertEquals(CONFLICT, denied.getStatusCode());
            return denied.getStatusCode();
        }
    }

    private static <T> Callable<T> afterGate(CountDownLatch gate, Callable<T> action) {
        return () -> {
            assertTrue(gate.await(10, TimeUnit.SECONDS));
            return action.call();
        };
    }
}
