package com.group.usbshield.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProvisioningService {
    private final AdminUserRepository users;
    private final WorkspaceRepository workspaces;
    private final PasswordEncoder encoder;
    private final WorkspaceAudit audit;

    @Transactional
    public void create(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 100 ||
                !AdminPasswordPolicy.acceptable(password)) {
            throw new IllegalArgumentException("Admin provisioning requires a valid username and password");
        }
        if (users.existsByUsername(username)) throw new IllegalStateException("Admin account already exists");
        AdminUser admin = users.saveAndFlush(AdminUser.builder()
                .username(username).passwordHash(encoder.encode(password)).build());
        Workspace workspace = workspaces.saveAndFlush(new Workspace(admin.getId()));
        audit.record(workspace.getId(), admin.getId(), "ADMIN_CREATED", admin.getUsername());
    }
}
