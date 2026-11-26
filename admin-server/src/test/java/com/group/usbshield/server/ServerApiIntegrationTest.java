package com.group.usbshield.server;

import com.fasterxml.jackson.databind.*;
import com.group.usbshield.server.auth.AdminProvisioningService;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.event.*;
import com.group.usbshield.server.device.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest
@AutoConfigureMockMvc
class ServerApiIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        com.group.usbshield.server.TestPostgresSchemas.register(registry, ServerApiIntegrationTest.class);
    }

    @Autowired WorkspaceTestData workspace;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired UsbEventRepository events;
    @Autowired AdminProvisioningService admins;
    MockHttpSession session;
    String csrf;
    record Agent(String id,String token){}
    @BeforeEach void setup() throws Exception {
        login(WorkspaceTestData.ADMIN);
    }

    private void login(String username) throws Exception {
        session=null;refreshCsrf();
        session=(MockHttpSession)mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",csrf)
            .contentType("application/json").content(mapper.writeValueAsString(
                    Map.of("username", username, "password", WorkspaceTestData.PASSWORD))))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        refreshCsrf();
    }
    void refreshCsrf() throws Exception {
        var request=get("/api/auth/csrf");if(session!=null)request.session(session);
        var result=mvc.perform(request).andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)result.getRequest().getSession();csrf=mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    Agent enroll() throws Exception {
        var installationId = UUID.randomUUID();
        var slot = createSlot(installationId);
        var id = slot.get("endpointId").asText();
        var issued = response(mvc.perform(post("/api/admin/enrollment/{id}/ticket", id)
                .session(session).header("X-CSRF-TOKEN", csrf)).andExpect(status().isCreated()));
        var request = claimData(installationId, issued.get("enrollmentToken").asText());
        var body = response(mvc.perform(claim(request)).andExpect(status().isOk()));
        assertThat(body.get("endpointId").asText()).isEqualTo(id);
        return new Agent(id, body.get("agentToken").asText());
    }

    private JsonNode createSlot(UUID installationId) throws Exception {
        return response(mvc.perform(slot(installationId)).andExpect(status().isCreated()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder slot(UUID installationId) throws Exception {
        return post("/api/admin/enrollment/slots").session(session).header("X-CSRF-TOKEN", csrf)
                .contentType("application/json").content(mapper.writeValueAsString(
                        Map.of("installationId", installationId, "hostname", "LAB")));
    }

    private Map<String, Object> claimData(UUID installationId, String ticket) {
        return new HashMap<>(Map.of("machineId", UUID.randomUUID(), "hostname", "LAB",
                "installationId", installationId, "requestId", UUID.randomUUID(),
                "enrollmentToken", ticket, "agentToken", WorkspaceTestData.digest(UUID.randomUUID().toString())));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder claim(Map<String, Object> body) throws Exception {
        return post("/api/agent/enroll").header("X-USBShield-Protocol", "2")
                .contentType("application/json").content(mapper.writeValueAsString(body));
    }

    private JsonNode response(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder agentPost(String path,Agent agent,Object body)throws Exception{
        return post(path).header("X-USBShield-Protocol", "2").header("X-Endpoint-Id",agent.id()).header("X-Agent-Token",agent.token()).contentType("application/json").content(mapper.writeValueAsString(body));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder policy(Agent a){return get("/api/agent/policy").header("X-USBShield-Protocol", "2").header("X-Endpoint-Id",a.id()).header("X-Agent-Token",a.token());}
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
        mvc.perform(post("/api/agent/enroll").contentType("application/json").content("{}"))
                .andExpect(status().isUpgradeRequired());
        mvc.perform(claim(Map.of())).andExpect(status().isBadRequest());
        var invalid = claimData(UUID.randomUUID(), "wrong");
        mvc.perform(claim(invalid)).andExpect(status().isUnauthorized());
        invalid.put("enrollmentToken", "x".repeat(257));
        mvc.perform(claim(invalid)).andExpect(status().isBadRequest());
        invalid.put("enrollmentToken", "wrong");
        invalid.put("ipAddress", "1".repeat(51));
        mvc.perform(claim(invalid)).andExpect(status().isBadRequest());
        var a=enroll();var e=endpoints.findByEndpointId(a.id()).orElseThrow();
        assertThat(e.getAgentTokenHash()).isEqualTo(WorkspaceTestData.digest(a.token()));
        mvc.perform(slot(e.getInstallationId())).andExpect(status().isConflict());
    }
    @Test void agentRoutesRejectMissingInvalidAndCrossEndpointCredentials() throws Exception {
        var a=enroll();var b=enroll();
        for(String route:List.of("/api/agent/heartbeat","/api/agent/events","/api/agent/policy/ack")) {
            mvc.perform(post(route).header("X-USBShield-Protocol", "2").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
            mvc.perform(post(route).header("X-USBShield-Protocol", "2").header("X-Endpoint-Id",a.id()).header("X-Agent-Token","wrong").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/agent/policy").header("X-USBShield-Protocol", "2")).andExpect(status().isUnauthorized());
        mvc.perform(policy(new Agent(b.id(), a.token()))).andExpect(status().isUnauthorized());
        mvc.perform(policy(a).param("endpointId",b.id())).andExpect(status().isForbidden());
        mvc.perform(agentPost("/api/agent/heartbeat",a,Map.of("endpointId",b.id()))).andExpect(status().isForbidden());
        mvc.perform(agentPost("/api/agent/events",a,sample(b))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/endpoints").header("X-Endpoint-Id",a.id()).header("X-Agent-Token",a.token())).andExpect(status().isUnauthorized());
    }

    @Test void workspaceOwnsCatalogMetadataPolicyAndAdminReadSurfaces() throws Exception {
        admins.create("scope-admin-a", WorkspaceTestData.PASSWORD);
        admins.create("scope-admin-b", WorkspaceTestData.PASSWORD);
        String url = "/api/admin/endpoints/{id}/whitelist";
        login("scope-admin-a");
        var a = enroll();
        var eventA = ingestWorkspaceEvent(a, "Storage A", "scope-host-a");
        long deviceA = eventA.path("usbDeviceId").asLong();
        long eventIdA = eventA.path("id").asLong();
        assertThat(deviceA).isPositive();
        var entry = response(mvc.perform(post(url, a.id()).session(session).header("X-CSRF-TOKEN", csrf)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("usbDeviceId", deviceA))))
                .andExpect(status().isOk()));
        long entryId = entry.path("id").asLong();
        var workspaceASession = session;

        login("scope-admin-b");
        var b = enroll();
        var eventB = ingestWorkspaceEvent(b, "Storage B", "scope-host-b");
        long deviceB = eventB.path("usbDeviceId").asLong();
        assertThat(deviceB).isPositive().isNotEqualTo(deviceA);
        mvc.perform(get("/api/admin/endpoints/page").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].endpointId").value(b.id()));
        mvc.perform(get("/api/admin/endpoints/{id}", a.id()).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/events/{id}", eventIdA).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/events").session(session).param("deviceId", Long.toString(deviceA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/events").session(session).param("endpoint", "scope-host-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/device-states").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Storage B"));
        mvc.perform(get("/api/admin/dashboard/summary").session(session)
                        .param("from", "2026-09-28T00:00:00Z").param("to", "2026-09-29T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalDevices").value(1))
                .andExpect(jsonPath("$.recentEvents").value(1));
        mvc.perform(get("/api/admin/whitelist/page").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(url, a.id()).session(session)).andExpect(status().isNotFound());
        mvc.perform(post(url, b.id()).session(session).header("X-CSRF-TOKEN", csrf)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("usbDeviceId", deviceA))))
                .andExpect(status().isNotFound());
        mvc.perform(delete(url + "/{entryId}", b.id(), entryId).session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/enrollment/{id}/ticket", a.id()).session(session).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/audit").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].actorUsername").value("scope-admin-b"))
                .andExpect(jsonPath("$.content[0].actorAdminId").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.targetId == '%s')]", a.id()).isEmpty());
        mvc.perform(policy(new Agent(a.id(), b.token()))).andExpect(status().isUnauthorized());
        mvc.perform(policy(b)).andExpect(status().isOk()).andExpect(jsonPath("$.whitelistedDevices").isEmpty());
        mvc.perform(get("/api/admin/events/{id}", eventIdA).session(workspaceASession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.device.name").value("Storage A"));
        mvc.perform(policy(a)).andExpect(status().isOk()).andExpect(jsonPath("$.whitelistedDevices.length()").value(1));
    }

    @Test void latestDeviceStateUsesIngestionOrderForEqualOccurredTime() throws Exception {
        var agent = enroll();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) sample(agent);
        payload.put("decision", "BLOCKED");
        var first = response(mvc.perform(agentPost("/api/agent/events", agent, payload)).andExpect(status().isOk()));
        payload.put("decision", "ALLOWED");
        var second = response(mvc.perform(agentPost("/api/agent/events", agent, payload)).andExpect(status().isOk()));
        assertThat(first.path("eventId").asLong()).isBetween(1L, 1L << 52);
        assertThat(second.path("eventId").asLong()).isBetween(1L, 1L << 52)
                .isNotEqualTo(first.path("eventId").asLong());
        mvc.perform(get("/api/admin/device-states").session(session).param("endpointId", agent.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("allowed"));
    }

    private JsonNode ingestWorkspaceEvent(Agent agent, String name, String hostname) throws Exception {
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) sample(agent);
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.get("device")).put("name", name);
        payload.put("hostname", hostname);
        var receipt = response(mvc.perform(agentPost("/api/agent/events", agent, payload)).andExpect(status().isOk()));
        return response(mvc.perform(get("/api/admin/events/{id}", receipt.path("eventId").asLong())
                .session(session)).andExpect(status().isOk()));
    }
    @Test void perEndpointAllowRevokeVersionAckAndCanonicalPolicy() throws Exception {
        var a=enroll();var b=enroll();
        var device=devices.save(UsbDevice.builder().workspaceId(workspace.workspaceId()).vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).deviceName("Storage").build());
        // Real endpoint A must have observed the device before it can whitelist it.
        events.save(UsbEvent.builder().workspaceId(workspace.workspaceId()).endpointId(a.id()).usbDeviceId(device.getId())
            .eventType("CONNECTED").decision("BLOCKED").occurredAt(java.time.Instant.now()).build());
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
    @Test void retryingSameEventKeyReturnsStoredEventInsteadOfDuplicatingAudit() throws Exception {
        var a = enroll();
        var b = enroll();
        String key = UUID.randomUUID().toString();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        payload.put("eventId", key);
        long before = events.count();
        var first = mvc.perform(agentPost("/api/agent/events", a, payload))
                .andExpect(status().isOk()).andReturn();
        var second = mvc.perform(agentPost("/api/agent/events", a, payload))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(first.getResponse().getContentAsString()).get("eventId").asLong())
                .isEqualTo(mapper.readTree(second.getResponse().getContentAsString()).get("eventId").asLong());
        assertThat(events.count()).isEqualTo(before + 1L);
        var tampered = payload.deepCopy();
        tampered.put("decision", "BLOCKED".equals(payload.get("decision").asText()) ? "ALLOWED" : "BLOCKED");
        mvc.perform(agentPost("/api/agent/events", a, tampered)).andExpect(status().isConflict());
        assertThat(events.count()).isEqualTo(before + 1L);
        var otherEndpoint = (com.fasterxml.jackson.databind.node.ObjectNode) sample(b);
        otherEndpoint.put("eventId", key);
        mvc.perform(agentPost("/api/agent/events", b, otherEndpoint)).andExpect(status().isOk());
        assertThat(events.count()).isEqualTo(before + 2L);
    }

    @Test void reusedEventIdRejectsChangesToEveryAuditRelevantField() throws Exception {
        var a = enroll();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        original.put("eventId", UUID.randomUUID().toString());
        mvc.perform(agentPost("/api/agent/events", a, original)).andExpect(status().isOk());
        long before = events.count();
        for (String field : List.of("linuxUid", "sessionId", "sessionType", "seat", "hostname")) {
            var modified = original.deepCopy();
            if ("linuxUid".equals(field)) modified.put(field, 999L);
            else modified.put(field, "different-" + field);
            mvc.perform(agentPost("/api/agent/events", a, modified)).andExpect(status().isConflict());
        }
        for (String field : List.of("vendorId", "productId", "serial", "name", "hash", "interface")) {
            var modified = original.deepCopy();
            var device = (com.fasterxml.jackson.databind.node.ObjectNode) modified.get("device");
            if ("vendorId".equals(field) || "productId".equals(field)) device.put(field, "1234");
            else device.put(field, "other-" + field);
            mvc.perform(agentPost("/api/agent/events", a, modified)).andExpect(status().isConflict());
        }
        mvc.perform(agentPost("/api/agent/events", a, original)).andExpect(status().isOk());
        assertThat(events.count()).isEqualTo(before);
    }

    @Test void preDigestEventsStillRequireAllPersistedFieldsToMatch() throws Exception {
        var a = enroll();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        original.put("eventId", UUID.randomUUID().toString());
        var created = mvc.perform(agentPost("/api/agent/events", a, original))
                .andExpect(status().isOk()).andReturn();
        long databaseId = mapper.readTree(created.getResponse().getContentAsString()).get("eventId").asLong();
        var stored = events.findById(databaseId).orElseThrow();
        stored.setPayloadDigest(null); // emulate the database before the new nullable column existed
        events.saveAndFlush(stored);
        mvc.perform(agentPost("/api/agent/events", a, original)).andExpect(status().isOk());
        var changed = original.deepCopy();
        changed.put("sessionId", "different-session");
        mvc.perform(agentPost("/api/agent/events", a, changed)).andExpect(status().isConflict());
    }

    @Test void concurrentDuplicateDeliveriesShareOneDatabaseEvent() throws Exception {
        var a = enroll();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        original.put("eventId", UUID.randomUUID().toString());
        long before = events.count();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var send = (java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult>) () -> {
                start.await();
                return mvc.perform(agentPost("/api/agent/events", a, original))
                        .andExpect(status().isOk()).andReturn();
            };
            var one = workers.submit(send);
            var two = workers.submit(send);
            start.countDown();
            var first = mapper.readTree(one.get(15, java.util.concurrent.TimeUnit.SECONDS)
                    .getResponse().getContentAsString());
            var second = mapper.readTree(two.get(15, java.util.concurrent.TimeUnit.SECONDS)
                    .getResponse().getContentAsString());
            assertThat(first.get("eventId").asLong()).isEqualTo(second.get("eventId").asLong());
        }
        assertThat(events.count()).isEqualTo(before + 1);
    }

    @Test void policyAppliedIsNotCountedAsAnotherConnectedEvent() throws Exception {
        var a = enroll();
        var initial = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        initial.put("eventId", UUID.randomUUID().toString());
        initial.put("eventType", "CONNECTED");
        initial.put("decision", "BLOCKED");
        mvc.perform(agentPost("/api/agent/events", a, initial)).andExpect(status().isOk());
        var applied = initial.deepCopy();
        applied.put("eventId", UUID.randomUUID().toString());
        applied.put("eventType", "POLICY_APPLIED");
        applied.put("decision", "ALLOWED");
        mvc.perform(agentPost("/api/agent/events", a, applied)).andExpect(status().isOk());
        var history = events.findByWorkspaceIdAndEndpointIdOrderByOccurredAtDesc(workspace.workspaceId(), a.id());
        assertThat(history.stream().filter(e -> "CONNECTED".equals(e.getEventType())).count()).isEqualTo(1L);
        assertThat(history.stream().filter(e -> "POLICY_APPLIED".equals(e.getEventType())).count()).isEqualTo(1L);
        mvc.perform(get("/api/admin/events").session(session).param("endpointId", a.id())
                .param("eventType", "POLICY_APPLIED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
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
        var first=devices.save(UsbDevice.builder().workspaceId(workspace.workspaceId()).vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).build());
        var second=devices.save(UsbDevice.builder().workspaceId(workspace.workspaceId()).vendorId("0951").productId("1666").deviceHash(UUID.randomUUID().toString()).build());
        // F10: concurrent Allow is only valid after this exact endpoint observed
        // both device IDs. Do not bypass production's isolation check in tests.
        for (var device : java.util.List.of(first, second)) {
            events.save(UsbEvent.builder().workspaceId(workspace.workspaceId()).endpointId(a.id()).usbDeviceId(device.getId())
                    .eventType("CONNECTED").decision("BLOCKED")
                    .occurredAt(java.time.Instant.now()).build());
        }
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var x=executor.submit(()->policyService.allow(workspace.workspaceId(), a.id(),new com.group.usbshield.server.policy.EndpointPolicyService.AllowRequest(first.getId())));
            var y=executor.submit(()->policyService.allow(workspace.workspaceId(), a.id(),new com.group.usbshield.server.policy.EndpointPolicyService.AllowRequest(second.getId())));
            x.get(15,java.util.concurrent.TimeUnit.SECONDS);y.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(endpoints.findByEndpointId(a.id()).orElseThrow().getPolicyVersion()).isEqualTo(3L);
        assertThat(policyService.list(workspace.workspaceId(), a.id())).hasSize(2);
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
    @Test void rejectsFutureTimestampButPreservesOldOfflineAudit() throws Exception {
        var a = enroll();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) sample(a);
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("occurredAt", java.time.Instant.now().plusSeconds(600).toString());
        long before = events.count();
        mvc.perform(agentPost("/api/agent/events", a, payload)).andExpect(status().isBadRequest());
        assertThat(events.count()).isEqualTo(before);
        payload.put("occurredAt", java.time.Instant.now().minusSeconds(90L * 24 * 3600).toString());
        mvc.perform(agentPost("/api/agent/events", a, payload)).andExpect(status().isOk());
        assertThat(events.count()).isEqualTo(before + 1L);
    }

    @Test void invalidHashCannotEnterWorkspaceCatalogOrBecomeAnAllowRule() throws Exception {
        var a = enroll();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> devices.saveAndFlush(UsbDevice.builder().workspaceId(workspace.workspaceId())
                        .vendorId("0951").productId("1666").deviceHash("0951:1666").build()));
        var d = devices.saveAndFlush(UsbDevice.builder().workspaceId(workspace.workspaceId())
                .vendorId("0951").productId("1666").deviceName("USB without a usable hash").build());
        events.save(UsbEvent.builder().workspaceId(workspace.workspaceId()).endpointId(a.id()).usbDeviceId(d.getId())
                .eventType("CONNECTED").decision("BLOCKED").occurredAt(java.time.Instant.now()).build());
        long before = endpoints.findByEndpointId(a.id()).orElseThrow().getPolicyVersion();
        mvc.perform(post("/api/admin/endpoints/" + a.id() + "/whitelist")
                .session(session).header("X-CSRF-TOKEN", csrf).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("usbDeviceId", d.getId()))))
                .andExpect(status().isConflict());
        assertThat(endpoints.findByEndpointId(a.id()).orElseThrow().getPolicyVersion()).isEqualTo(before);
    }

}
