package com.group.usbshield.server;

import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.policy.EndpointPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:week3;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "usbshield.offline-after-seconds=90"})
@AutoConfigureMockMvc
class Week3SecurityAndStatusTest {
    @Autowired MockMvc mvc;
    @Autowired EndpointRepository endpoints;
    @Autowired EndpointPolicyService policies;

    Endpoint endpoint(String hash) {
        return endpoints.saveAndFlush(Endpoint.builder().endpointId("W3-"+UUID.randomUUID())
                .hostname("week3").agentTokenHash(hash).build());
    }

    @Test @WithMockUser(roles="ADMIN")
    void noHeartbeatAndExpiredHeartbeatAreOffline() throws Exception {
        var e=endpoint(null);
        assertThat(e.getLastSeen()).isNull();
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFLINE"));
        e.setLastSeen(Instant.now().minusSeconds(95));endpoints.saveAndFlush(e);
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId())).andExpect(jsonPath("$.status").value("OFFLINE"));
        e.setLastSeen(Instant.now());endpoints.saveAndFlush(e);
        mvc.perform(get("/api/admin/endpoints/"+e.getEndpointId())).andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test @WithMockUser(roles="ADMIN")
    void adminSessionDoesNotBypassAgentCredentialsAndCorruptHashesFailClosed() throws Exception {
        for(String hash:new String[]{"not-a-hash", "$2a$99$"+"A".repeat(53)}) {
            var e=endpoint(hash);
            mvc.perform(get("/api/agent/policy").header("X-Endpoint-Id",e.getEndpointId()).header("X-Agent-Token","bad"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/agent/policy")).andExpect(status().isUnauthorized());
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
        policies.ack(a.getEndpointId(),3L);
        assertThat(policies.policy(a.getEndpointId()).getPolicyVersion()).isEqualTo(4L);
        policies.ack(a.getEndpointId(),3L);
        assertThat(endpoints.findByEndpointId(a.getEndpointId()).orElseThrow().getAppliedPolicyVersion()).isEqualTo(3L);
        assertThat(endpoints.findByEndpointId(b.getEndpointId()).orElseThrow().getAppliedPolicyVersion()).isZero();
    }

    @Test void onlyNavigationRoutesRedirectToHashSpa() throws Exception {
        mvc.perform(get("/events")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#/events"));
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/endpoints")).andExpect(status().isUnauthorized());
    }
}
