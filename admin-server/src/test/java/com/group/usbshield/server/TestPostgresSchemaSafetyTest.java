package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPostgresSchemaSafetyTest {
    @Test void onlyClassOwnedRandomIntegrationSchemasCanBeDeleted() {
        assertDoesNotThrow(() -> TestPostgresSchemas.assertSafeSchemaName(
                "usbshield_it_0123456789abcdef0123456789abcdef"));
        for (String name : new String[]{"public", "usbshield", "usbshield_test",
                "usbshield_e2e_0123456789abcdef0123456789abcdef", "usbshield_it_0123'CASCADE"}) {
            assertThrows(SecurityException.class, () -> TestPostgresSchemas.assertSafeSchemaName(name));
        }
    }
}
