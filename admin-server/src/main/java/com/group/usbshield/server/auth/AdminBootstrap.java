package com.group.usbshield.server.auth;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @org.springframework.context.annotation.Profile("!admin-reset & !admin-provision")
public class AdminBootstrap implements ApplicationRunner {
    private final AdminUserRepository users;
    private final AdminProvisioningService provisioning;
    @Value("${usbshield.admin.username:admin}") private String username;
    @Value("${usbshield.admin.password:}") private String password;
    public void run(ApplicationArguments args) {
        if (password.isBlank() || users.count() != 0) return;
        if (!AdminPasswordPolicy.acceptable(password)) {
            throw new IllegalStateException("Initial Admin password must satisfy AdminPasswordPolicy (12+ printable characters, <=72 UTF-8 bytes)");
        }
        provisioning.create(username, password);
    }
}
