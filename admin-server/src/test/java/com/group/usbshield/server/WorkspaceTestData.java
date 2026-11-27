package com.group.usbshield.server;

import com.group.usbshield.server.auth.AdminIdentity;
import com.group.usbshield.server.auth.AdminSessionVersionFilter;
import com.group.usbshield.server.auth.AdminUserRepository;
import com.group.usbshield.server.auth.WorkspaceRepository;
import com.group.usbshield.server.endpoint.Endpoint;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Scoped data for tests that exercise queries or Agent writes independently of Admin login. */
public record WorkspaceTestData(AdminUserRepository users, WorkspaceRepository workspaces) {
    public static final String ADMIN = "test-admin";
    public static final String PASSWORD = "test-password";

    public AdminIdentity admin() {
        var admin = users.findByUsername(ADMIN).orElseThrow();
        var workspace = workspaces.findByOwnerAdminId(admin.getId()).orElseThrow();
        return new AdminIdentity(admin.getId(), workspace.getId(), admin.getUsername());
    }

    public long workspaceId() {
        return admin().workspaceId();
    }

    public Endpoint.EndpointBuilder endpoint() {
        return Endpoint.builder().workspaceId(workspaceId()).installationId(UUID.randomUUID());
    }

    public RequestPostProcessor adminRequest() {
        var identity = admin();
        var version = users.findById(identity.adminUserId()).orElseThrow().getCredentialVersion();
        var auth = new UsernamePasswordAuthenticationToken(identity, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return request -> {
            request.getSession().setAttribute(AdminSessionVersionFilter.SESSION_VERSION, version);
            return authentication(auth).postProcessRequest(request);
        };
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
