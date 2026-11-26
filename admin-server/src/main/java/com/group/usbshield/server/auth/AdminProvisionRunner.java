package com.group.usbshield.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("admin-provision")
@RequiredArgsConstructor
public class AdminProvisionRunner implements ApplicationRunner {
    private final AdminProvisioningService provisioning;
    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        if (!"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type", ""))) {
            throw new IllegalStateException("Admin provisioning requires the no-web profile");
        }
        provisioning.create(env.getProperty("USBSHIELD_ADMIN_PROVISION_USERNAME"),
                env.getProperty("USBSHIELD_ADMIN_PROVISION_PASSWORD"));
    }
}
