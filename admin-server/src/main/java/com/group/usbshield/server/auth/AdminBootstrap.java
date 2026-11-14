package com.group.usbshield.server.auth;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @org.springframework.context.annotation.Profile("!admin-reset")
public class AdminBootstrap implements ApplicationRunner {
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    @Value("${usbshield.admin.username:admin}") private String username;
    @Value("${usbshield.admin.password:}") private String password;
    public void run(ApplicationArguments args) {
        if (password.isBlank() || users.existsByUsername(username)) return;
        if (!AdminPasswordPolicy.acceptable(password)) {
            throw new IllegalStateException("Initial Admin password must satisfy AdminPasswordPolicy (12+ printable characters, <=72 UTF-8 bytes)");
        }
        users.save(AdminUser.builder().username(username).passwordHash(encoder.encode(password)).build());
    }
}
