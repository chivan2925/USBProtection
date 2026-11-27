package com.group.usbshield.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.server.auth.AdminProvisioningService;
import com.group.usbshield.server.auth.AdminUserRepository;
import com.group.usbshield.server.auth.WorkspaceRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminCredentialIntegrationTest {
    private static final String CHANGE = "/api/admin/account/credentials";
    private static final String ORIGINAL = "synthetic-original-secret";
    private static final String NEW_PASSWORD = "synthetic-new-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminProvisioningService provisioning;
    @Autowired AdminUserRepository users;
    @Autowired WorkspaceRepository workspaces;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestPostgresSchemas.register(registry, AdminCredentialIntegrationTest.class);
    }

    @Test void renameKeepsWorkspaceAndPasswordAndRevokesPriorSessions() throws Exception {
        provisioning.create("settings-rename-before", ORIGINAL);
        var original = users.findByUsername("settings-rename-before").orElseThrow();
        long workspace = workspaces.findByOwnerAdminId(original.getId()).orElseThrow().getId();
        var primary = login("settings-rename-before", ORIGINAL);
        var peer = login("settings-rename-before", ORIGINAL);
        var unrelated = login(WorkspaceTestData.ADMIN, WorkspaceTestData.PASSWORD);

        mvc.perform(change(primary, Map.of("currentPassword", ORIGINAL, "newUsername", "settings-rename-after")))
                .andExpect(status().isNoContent());
        assertThat(primary.session().isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").servletPath("/api/auth/me").session(peer.session()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").servletPath("/api/auth/me").session(unrelated.session()))
                .andExpect(status().isOk());
        mvc.perform(loginRequest("settings-rename-before", ORIGINAL)).andExpect(status().isUnauthorized());

        var renamed = users.findByUsername("settings-rename-after").orElseThrow();
        assertThat(renamed.getId()).isEqualTo(original.getId());
        assertThat(workspaces.findByOwnerAdminId(renamed.getId()).orElseThrow().getId()).isEqualTo(workspace);
        var next = login("settings-rename-after", ORIGINAL);
        mvc.perform(get("/api/admin/audit").session(next.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_USERNAME_CHANGED"))
                .andExpect(jsonPath("$.content[0].targetId").value(original.getId().toString()));
    }

    @Test void protectsMutationsAndChangesPasswordAlone() throws Exception {
        String username = "settings-rotate";
        provisioning.create(username, ORIGINAL);
        var current = login(username, ORIGINAL);
        var anonymous = csrf(null);
        var valid = Map.of("currentPassword", ORIGINAL, "newPassword", NEW_PASSWORD);

        mvc.perform(change(anonymous, valid)).andExpect(status().isUnauthorized());
        mvc.perform(post(CHANGE).session(current.session()).contentType("application/json")
                .content(json.writeValueAsString(valid))).andExpect(status().isForbidden());
        mvc.perform(change(current, Map.of("currentPassword", "synthetic-wrong-secret", "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(change(current, Map.of("currentPassword", "é".repeat(37), "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(change(current, Map.of("currentPassword", ORIGINAL, "newPassword", "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(change(current, Map.of("currentPassword", ORIGINAL, "newPassword", ORIGINAL)))
                .andExpect(status().isBadRequest());
        mvc.perform(change(current, Map.of("currentPassword", ORIGINAL, "newUsername", username)))
                .andExpect(status().isBadRequest());
        assertThat(users.findByUsername(username).orElseThrow().getCredentialVersion()).isZero();

        mvc.perform(change(current, valid)).andExpect(status().isNoContent());
        mvc.perform(loginRequest(username, ORIGINAL)).andExpect(status().isUnauthorized());
        var next = login(username, NEW_PASSWORD);
        mvc.perform(get("/api/admin/audit").session(next.session()))
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_PASSWORD_CHANGED"));
    }

    @Test void collisionMakesCombinedChangeAtomic() throws Exception {
        provisioning.create("settings-combined", ORIGINAL);
        provisioning.create("settings-occupied", ORIGINAL);
        var current = login("settings-combined", ORIGINAL);
        mvc.perform(change(current, Map.of("currentPassword", ORIGINAL, "newUsername", "settings-occupied",
                        "newPassword", NEW_PASSWORD)))
                .andExpect(status().isConflict());
        assertThat(users.findByUsername("settings-combined").orElseThrow().getCredentialVersion()).isZero();
        mvc.perform(loginRequest("settings-combined", NEW_PASSWORD)).andExpect(status().isUnauthorized());
        login("settings-occupied", ORIGINAL);
        mvc.perform(change(current, Map.of("currentPassword", ORIGINAL, "newUsername", "settings-combined-after",
                        "newPassword", NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(loginRequest("settings-combined-after", ORIGINAL)).andExpect(status().isUnauthorized());
        login("settings-combined-after", NEW_PASSWORD);
    }

    @Test void traceOutputContainsNoPasswordOrBodyBytesForCredentialChanges(CapturedOutput output) throws Exception {
        provisioning.create("settings-trace", ORIGINAL);
        var session = login("settings-trace", ORIGINAL);
        String marker = "syntheticTracePasswordMarker";
        String decimalMarker = marker.chars().mapToObj(Integer::toString).collect(Collectors.joining(", "));
        var logging = LoggingSystem.get(getClass().getClassLoader());
        String logger = "org.springframework.web.servlet.mvc.method.annotation";
        var configuration = logging.getLoggerConfiguration(logger);
        var previous = configuration == null ? null : configuration.getConfiguredLevel();
        logging.setLogLevel(logger, LogLevel.TRACE);
        try {
            mvc.perform(change(session, Map.of("currentPassword", marker.repeat(6), "newPassword", NEW_PASSWORD)))
                    .andExpect(status().isBadRequest());
            mvc.perform(changeBody(session, "{\"currentPassword\":" + marker + "}")).andExpect(status().isBadRequest());
            mvc.perform(changeBody(session, "{\"currentPassword\":\"" + marker.repeat(200) + "\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(change(session, Map.of("currentPassword", ORIGINAL, "newPassword", marker)))
                    .andExpect(status().isNoContent());
        } finally {
            logging.setLogLevel(logger, previous);
        }
        assertThat(output.getAll()).contains("Invalid request fields")
                .doesNotContain(marker, decimalMarker, "{123, 34,");
    }

    private record Session(MockHttpSession session, String csrf) {}

    private Session csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false),
                json.readTree(result.getResponse().getContentAsByteArray()).required("token").asText());
    }

    private Session login(String username, String password) throws Exception {
        var result = mvc.perform(loginRequest(username, password)).andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession) result.getRequest().getSession(false));
    }

    private MockHttpServletRequestBuilder loginRequest(String username, String password) throws Exception {
        var anonymous = csrf(null);
        return post("/api/auth/login").session(anonymous.session()).header("X-CSRF-TOKEN", anonymous.csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private MockHttpServletRequestBuilder change(Session session, Map<String, String> body) throws Exception {
        return changeBody(session, json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder changeBody(Session session, String body) {
        return post(CHANGE).servletPath(CHANGE).session(session.session()).header("X-CSRF-TOKEN", session.csrf())
                .contentType("application/json").content(body);
    }
}
