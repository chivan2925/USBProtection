package com.group.usbshield.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class UsbShieldServerApplication {
    public static void main(String[] args) {
        // Local test maintenance is deliberately handled BEFORE any Spring/JPA/Flyway startup.
        // A browser-test cleanup command cannot touch the application datasource.
        try {
            if (TestBrowserSchemaCleanup.handle(args)) return;
        } catch (Exception problem) {
            throw new IllegalStateException("Browser test schema cleanup refused or failed", problem);
        }
        ConfigurableApplicationContext context = SpringApplication.run(UsbShieldServerApplication.class, args);
        // Maintenance must run to completion without opening HTTP, then close
        // database connections and exit. The normal server keeps running.
        if (context.getEnvironment().acceptsProfiles(Profiles.of("admin-reset"))) {
            SpringApplication.exit(context);
        }
    }
}
