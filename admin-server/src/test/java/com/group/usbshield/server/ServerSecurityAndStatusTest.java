package com.group.usbshield.server;

import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.policy.EndpointPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "usbshield.offline-after-seconds=90"})
@AutoConfigureMockMvc
class ServerSecurityAndStatusTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        com.group.usbshield.server.TestPostgresSchemas.register(registry, ServerSecurityAndStatusTest.class);
    }

    @Autowired WorkspaceTestData workspace;
    @Autowired MockMvc mvc;
    @Autowired EndpointRepository endpoints;
    @Autowired EndpointPolicyService policies;

    Endpoint endpoint(String hash) {
        return endpoints.saveAndFlush(workspace.endpoint().endpointId("W3-"+UUID.randomUUID())
                .hostname("week3").agentTokenHash(hash).build());
    }

    @Test
    void noHeartbeatAndExpiredHeartbeatAreOffline() throws Exception {
        var e=endpoint(null);
        assertThat(e.getLastSeen()).isNull();
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId()).with(workspace.adminRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFLINE"));
        e.setLastSeen(Instant.now().minusSeconds(95));endpoints.saveAndFlush(e);
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId()).with(workspace.adminRequest())).andExpect(jsonPath("$.status").value("OFFLINE"));
        e.setLastSeen(Instant.now());endpoints.saveAndFlush(e);
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId()).with(workspace.adminRequest())).andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void adminSessionDoesNotBypassAgentCredentialsAndCorruptHashesFailClosed() throws Exception {
        for(String hash:new String[]{"not-a-hash", "$2a$99$"+"A".repeat(53)}) {
            var e=endpoint(hash);
            mvc.perform(get("/api/agent/policy").header("X-USBShield-Protocol", "2").header("X-Endpoint-Id",e.getEndpointId()).with(workspace.adminRequest()).header("X-Agent-Token",WorkspaceTestData.digest("wrong")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/agent/policy").header("X-USBShield-Protocol", "2")).andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedUtf8AgentTokenIsRejectedAsUnauthorized() throws Exception {
        var e=endpoint(WorkspaceTestData.digest("expected-agent-token"));
        mvc.perform(get("/api/agent/policy").header("X-USBShield-Protocol", "2")
                        .header("X-Endpoint-Id",e.getEndpointId())
                        .header("X-Agent-Token","é".repeat(72)))
                .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles="AGENT")
    void agentRoleCannotReadAdminDataOrMutateWithNoCsrf() throws Exception {
        mvc.perform(get("/api/admin/endpoints")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/endpoints/any/whitelist").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void pendingPolicyAckCanCatchUpWithoutChangingDesiredOrAnotherEndpoint() {
        var a=endpoint(null);var b=endpoint(null);
        a.setPolicyVersion(4L);a.setAppliedPolicyVersion(1L);endpoints.saveAndFlush(a);
        policies.ack(workspace.workspaceId(), a.getEndpointId(),3L);
        assertThat(policies.policy(workspace.workspaceId(), a.getEndpointId()).getPolicyVersion()).isEqualTo(4L);
        policies.ack(workspace.workspaceId(), a.getEndpointId(),3L);
        assertThat(endpoints.findByEndpointId(a.getEndpointId()).orElseThrow().getAppliedPolicyVersion()).isEqualTo(3L);
        assertThat(endpoints.findByEndpointId(b.getEndpointId()).orElseThrow().getAppliedPolicyVersion()).isZero();
    }

    @Test void formerWebEntrypointsAreAbsentAndApisRequireAuthentication() throws Exception {
        for (String route : new String[]{"/", "/events", "/login", "/dashboard", "/index.html", "/assets/missing.js", "/swagger-ui.html"}) {
            mvc.perform(get(route)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/admin/endpoints")).andExpect(status().isUnauthorized());
    }
    @Test
    void usbGuardStatusIsStaleOfflineOrPolicyMismatchEvenIfAgentClaimedHealthy() throws Exception {
        var e = endpoint(null);
        e.setLastSeen(Instant.now());
        e.setUsbGuardReportedStatus("HEALTHY");
        e.setUsbGuardCheckedAt(Instant.now());
        e.setPolicyVersion(2L);e.setAppliedPolicyVersion(2L);endpoints.saveAndFlush(e);
        String url = "/api/admin/endpoints/" + e.getEndpointId();
        mvc.perform(get(url).with(workspace.adminRequest())).andExpect(jsonPath("$.usbGuardStatus").value("HEALTHY"));
        e.setAppliedPolicyVersion(1L);endpoints.saveAndFlush(e);
        mvc.perform(get(url).with(workspace.adminRequest())).andExpect(jsonPath("$.usbGuardStatus").value("DEGRADED"));
        e.setAppliedPolicyVersion(2L);
        e.setUsbGuardCheckedAt(Instant.now().minusSeconds(200));endpoints.saveAndFlush(e);
        mvc.perform(get(url).with(workspace.adminRequest())).andExpect(jsonPath("$.usbGuardStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.usbGuardStale").value(true));
        e.setLastSeen(Instant.now().minusSeconds(200));e.setUsbGuardCheckedAt(Instant.now());endpoints.saveAndFlush(e);
        mvc.perform(get(url).with(workspace.adminRequest())).andExpect(jsonPath("$.usbGuardStatus").value("UNKNOWN"));
    }

}
