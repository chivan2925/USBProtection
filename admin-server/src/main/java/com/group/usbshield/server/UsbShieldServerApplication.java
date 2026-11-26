package com.group.usbshield.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
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
        SpringApplication application = new SpringApplication(UsbShieldServerApplication.class);
        application.addInitializers(context -> {
            var profiles = context.getEnvironment();
            boolean reset = profiles.acceptsProfiles(Profiles.of("admin-reset"));
            boolean provision = profiles.acceptsProfiles(Profiles.of("admin-provision"));
            if (reset && provision) throw new IllegalStateException("Select a single Admin maintenance action");
            if ((reset || provision) && context instanceof WebServerApplicationContext) {
                throw new IllegalStateException("Admin maintenance requires a non-web process");
            }
        });
        ConfigurableApplicationContext context = application.run(args);
        // Maintenance must run to completion without opening HTTP, then close
        // database connections and exit. The normal server keeps running.
        if (context.getEnvironment().acceptsProfiles(Profiles.of("admin-reset", "admin-provision"))) {
            SpringApplication.exit(context);
        }
    }
}
