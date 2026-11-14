package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserSchemaCleanupGuardTest {
    @Test void onlyDisposableBrowserSchemaNamesCanBeDeleted() {
        assertDoesNotThrow(() -> TestBrowserSchemaCleanup.assertAllowedSchema(
                "usbshield_e2e_0123456789abcdef0123456789abcdef"));
        for (String name : new String[] {"public", "usbshield", "usbshield_test", "usbshield_e2e_", 
                "usbshield_e2e_0123456789abcdef0123456789abcdef;drop schema public", 
                "usbshield_it_0123456789abcdef0123456789abcdef"}) {
            assertThrows(SecurityException.class, () -> TestBrowserSchemaCleanup.assertAllowedSchema(name));
        }
    }
    @Test void ordinaryBootArgumentsDoNotTriggerCleanup() throws Exception {
        assertFalse(TestBrowserSchemaCleanup.handle(new String[]{"--server.port=8080"}));
        assertFalse(TestBrowserSchemaCleanup.handle(new String[]{}));
    }
}
