package com.group.usbshield.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** One-shot, no-HTTP recovery. Never enabled by merely setting a password environment variable. */
@Component
@Profile("admin-reset")
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements ApplicationRunner {
    private final AdminUserRepository accounts;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Environment env;

    @Override public void run(ApplicationArguments args) {
        if (!"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type", "")))
            throw new IllegalStateException("Admin recovery must run with the no-web application profile");
        String password = env.getProperty("USBSHIELD_ADMIN_RESET_PASSWORD");
        String username = env.getProperty("USBSHIELD_ADMIN_RESET_USERNAME",
                env.getProperty("usbshield.admin.username", "admin"));
        if (!AdminPasswordPolicy.acceptable(password))
            throw new IllegalStateException("Admin reset requires a new printable secret with at least 12 characters and at most 72 UTF-8 bytes in USBSHIELD_ADMIN_RESET_PASSWORD");
        transactions.executeWithoutResult(status -> {
            var admin = accounts.lockByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Cannot reset a non-existing Admin account"));
            if (encoder.matches(password, admin.getPasswordHash()))
                throw new IllegalStateException("New password must differ");
            admin.setPasswordHash(encoder.encode(password));
            admin.setCredentialVersion((admin.getCredentialVersion() == null ? 0L : admin.getCredentialVersion()) + 1);
            accounts.saveAndFlush(admin);
        });
        // No password value or hash in logs.
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Admin password reset completed; all previous sessions revoked");
    }
}
