package com.group.usbshield.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class UsbShieldServerApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(UsbShieldServerApplication.class, args);
        // Maintenance must run to completion without opening HTTP, then close
        // database connections and exit. The normal server keeps running.
        if (context.getEnvironment().acceptsProfiles(Profiles.of("admin-reset"))) {
            SpringApplication.exit(context);
        }
    }
}
