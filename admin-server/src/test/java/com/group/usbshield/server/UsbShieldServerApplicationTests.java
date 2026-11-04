package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest
class UsbShieldServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
