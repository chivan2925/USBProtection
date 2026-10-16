package com.group.usbshield.server;

import com.fasterxml.jackson.databind.*;
import com.group.usbshield.server.auth.*;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.event.*;
import com.group.usbshield.server.device.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:week2;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","usbshield.enrollment-token=integration-test-bootstrap"})
@AutoConfigureMockMvc
class Week2ServerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminUserRepository users;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired UsbEventRepository events;
    @Autowired PasswordEncoder encoder;
    MockHttpSession session;
    String csrf;
    record Agent(String id,String token){}
    @BeforeEach void setup() throws Exception {
        if(!users.existsByUsername("test-admin"))users.save(AdminUser.builder().username("test-admin").passwordHash(encoder.encode("test-password")).build());
        session=null;refreshCsrf();
        session=(MockHttpSession)mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",csrf)
            .contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        refreshCsrf();
    }
    void refreshCsrf() throws Exception {
        var request=get("/api/auth/csrf");if(session!=null)request.session(session);
        var result=mvc.perform(request).andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)result.getRequest().getSession();csrf=mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    Agent enroll() throws Exception {
        var result=mvc.perform(post("/api/agent/enroll").contentType("application/json").content(mapper.writeValueAsString(Map.of(
            "machineId",UUID.randomUUID().toString(),"hostname","LAB-"+UUID.randomUUID(),"enrollmentToken","integration-test-bootstrap"))))
            .andExpect(status().isOk()).andReturn();
        var body=mapper.readTree(result.getResponse().getContentAsString());return new Agent(body.get("endpointId").asText(),body.get("agentToken").asText());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder agentPost(String path,Agent agent,Object body)throws Exception{
        return post(path).header("X-Endpoint-Id",agent.id()).header("X-Agent-Token",agent.token()).contentType("application/json").content(mapper.writeValueAsString(body));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder policy(Agent a){return get("/api/agent/policy").header("X-Endpoint-Id",a.id()).header("X-Agent-Token",a.token());}
    JsonNode sample(Agent a)throws Exception {
        Path p=Path.of("../docs/agent-contract/sample-usb-event.json");if(!Files.exists(p))p=Path.of("docs/agent-contract/sample-usb-event.json");
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Files.readString(p));node.put("endpointId",a.id());return node;
    }
    @Test void adminSessionAndCsrfLifecycle() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/endpoints")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("test-admin"));
        mvc.perform(post("/api/admin/endpoints/missing/whitelist").session(session).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"wrong\"}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void enrollmentRejectsInvalidAndDuplicateAndHashesCredential() throws Exception {
        mvc.perform(post("/api/agent/enroll").contentType("application/json").content("{\"enrollmentToken\":\"wrong\"}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/agent/enroll").contentType("application/json").content("{\"enrollmentToken\":\"integration-test-bootstrap\"}" )).andExpect(status().isBadRequest());
        var a=enroll();var e=endpoints.findByEndpointId(a.id()).orElseThrow();
        assertThat(e.getAgentTokenHash()).isNotEqualTo(a.token());assertThat(encoder.matches(a.token(),e.getAgentTokenHash())).isTrue();
        mvc.perform(post("/api/agent/enroll").contentType("application/json").content(mapper.writeValueAsString(Map.of("machineId",e.getMachineId(),"hostname","duplicate","enrollmentToken","integration-test-bootstrap"))))
            .andExpect(status().isConflict());
    }
    @Test void agentRoutesRejectMissingInvalidAndCrossEndpointCredentials() throws Exception {
        var a=enroll();var b=enroll();
        for(String route:List.of("/api/agent/heartbeat","/api/agent/events","/api/agent/policy/ack")) {
            mvc.perform(post(route).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
            mvc.perform(post(route).header("X-Endpoint-Id",a.id()).header("X-Agent-Token","wrong").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/agent/policy")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agent/policy").header("X-Endpoint-Id",b.id()).header("X-Agent-Token",a.token())).andExpect(status().isUnauthorized());
        mvc.perform(policy(a).param("endpointId",b.id())).andExpect(status().isForbidden());
        mvc.perform(agentPost("/api/agent/heartbeat",a,Map.of("endpointId",b.id()))).andExpect(status().isForbidden());
        mvc.perform(agentPost("/api/agent/events",a,sample(b))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/endpoints").header("X-Endpoint-Id",a.id()).header("X-Agent-Token",a.token())).andExpect(status().isUnauthorized());
    }
    @Test void perEndpointAllowRevokeVersionAckAndCanonicalPolicy() throws Exception {
        var a=enroll();var b=enroll();
        var device=devices.save(UsbDevice.builder().vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).deviceName("Storage").build());
        String url="/api/admin/endpoints/"+a.id()+"/whitelist";
        mvc.perform(policy(a)).andExpect(status().isOk()).andExpect(jsonPath("$.whitelistedDevices").isEmpty()).andExpect(jsonPath("$.allowedDevices").doesNotExist());
        var result=mvc.perform(post(url).session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content(mapper.writeValueAsString(Map.of("usbDeviceId",device.getId(),"endpointId",b.id())))).andExpect(status().isOk()).andReturn();
        long entry=mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post(url).session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content(mapper.writeValueAsString(Map.of("usbDeviceId",device.getId())))).andExpect(status().isOk());
        assertThat(endpoints.findByEndpointId(a.id()).orElseThrow().getPolicyVersion()).isEqualTo(2L);
        assertThat(endpoints.findByEndpointId(b.id()).orElseThrow().getPolicyVersion()).isEqualTo(1L);
        mvc.perform(policy(a)).andExpect(jsonPath("$.whitelistedDevices[0].deviceHash").value(device.getDeviceHash())).andExpect(jsonPath("$.policyVersion").value(2));
        mvc.perform(policy(b)).andExpect(jsonPath("$.whitelistedDevices").isEmpty());
        mvc.perform(agentPost("/api/agent/heartbeat",a,Map.of("endpointId",a.id(),"currentPolicyVersion",999))).andExpect(status().isOk()).andExpect(jsonPath("$.lastAppliedPolicyVersion").value(0));
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",-1))).andExpect(status().isBadRequest());
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",3))).andExpect(status().isConflict());
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",2))).andExpect(status().isOk());
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",2))).andExpect(status().isOk());
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",1))).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/endpoints/"+b.id()+"/whitelist/"+entry).session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isNotFound());
        for(int i=0;i<2;i++)mvc.perform(delete(url+"/"+entry).session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isNoContent());
        mvc.perform(policy(a)).andExpect(jsonPath("$.whitelistedDevices").isEmpty()).andExpect(jsonPath("$.policyVersion").value(3));
        assertThat(endpoints.findByEndpointId(b.id()).orElseThrow().getPolicyVersion()).isEqualTo(1L);
        mvc.perform(agentPost("/api/agent/policy/ack",a,Map.of("appliedPolicyVersion",3))).andExpect(status().isOk());
    }
    @Test void ingestedEventCanBeFilteredPagedAndQueriedWithoutCredentialsLeaking() throws Exception {
        var a=enroll();var payload=sample(a);
        var result=mvc.perform(agentPost("/api/agent/events",a,payload)).andExpect(status().isOk()).andReturn();
        long eventId=mapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();
        var stored=events.findById(eventId).orElseThrow();assertThat(stored.getSessionId()).isEqualTo("21");assertThat(stored.getDecision()).isEqualTo("ALLOWED");
        mvc.perform(get("/api/admin/events").session(session).param("endpointId",a.id()).param("linuxUsername","usbdev").param("decision","ALLOWED").param("eventType","CONNECTED").param("deviceId",stored.getUsbDeviceId().toString()).param("from","2026-09-28T07:00:00Z").param("to","2026-09-28T08:00:00Z").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].sessionId").value("21")).andExpect(jsonPath("$.content[0].device.name").value("USB Optical Mouse"));
        mvc.perform(get("/api/admin/events").session(session).param("endpointId",a.id()).param("decision","BLOCKED")).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/admin/events").session(session).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/events/"+eventId).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/endpoints/"+a.id()).session(session)).andExpect(jsonPath("$.agentTokenHash").doesNotExist()).andExpect(jsonPath("$.hostname").isNotEmpty());
        var invalid=(com.fasterxml.jackson.databind.node.ObjectNode)payload;invalid.put("decision","MAYBE");
        mvc.perform(agentPost("/api/agent/events",a,invalid)).andExpect(status().isBadRequest());
    }

    @Autowired com.group.usbshield.server.policy.EndpointPolicyService policyService;

    @Test void concurrentWhitelistChangesDoNotLoseDesiredVersion() throws Exception {
        var a=enroll();
        var first=devices.save(UsbDevice.builder().vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).build());
        var second=devices.save(UsbDevice.builder().vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).build());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var x=executor.submit(()->policyService.allow(a.id(),new com.group.usbshield.server.policy.EndpointPolicyService.AllowRequest(first.getId())));
            var y=executor.submit(()->policyService.allow(a.id(),new com.group.usbshield.server.policy.EndpointPolicyService.AllowRequest(second.getId())));
            x.get(15,java.util.concurrent.TimeUnit.SECONDS);y.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(endpoints.findByEndpointId(a.id()).orElseThrow().getPolicyVersion()).isEqualTo(3L);
        assertThat(policyService.list(a.id())).hasSize(2);
    }

    @Test void unknownUserAndOfflineStateRemainTruthful() throws Exception {
        var a=enroll();var payload=(com.fasterxml.jackson.databind.node.ObjectNode)sample(a);
        payload.put("linuxUsername"," ");
        var result=mvc.perform(agentPost("/api/agent/events",a,payload)).andExpect(status().isOk()).andReturn();
        long id=mapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();
        var event=events.findById(id).orElseThrow();
        assertThat(event.getLinuxUsername()).isEqualTo("UNKNOWN");
        assertThat(event.getLinuxUid()).isNull();assertThat(event.getSessionId()).isNull();
        var endpoint=endpoints.findByEndpointId(a.id()).orElseThrow();endpoint.setLastSeen(java.time.Instant.now().minusSeconds(120));endpoints.save(endpoint);
        mvc.perform(get("/api/admin/endpoints/"+a.id()).session(session)).andExpect(jsonPath("$.status").value("OFFLINE"));
        mvc.perform(agentPost("/api/agent/heartbeat",a,Map.of("endpointId",a.id()))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/endpoints/"+a.id()).session(session)).andExpect(jsonPath("$.status").value("ONLINE"));
    }
}
