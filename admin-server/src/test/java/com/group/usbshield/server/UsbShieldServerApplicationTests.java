package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest
class UsbShieldServerApplicationTests {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        com.group.usbshield.server.TestPostgresSchemas.register(registry, UsbShieldServerApplicationTests.class);
    }


    @Test
    void contextLoads() {
    }
}
