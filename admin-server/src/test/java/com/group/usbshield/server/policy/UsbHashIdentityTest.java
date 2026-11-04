package com.group.usbshield.server.policy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UsbHashIdentityTest {
    @Test void rejectsUnsafeAndModelOnlyIdentities() {
        for (String s : new String[]{"", " ", "0951:1666", "hash \"abc\"", "abc\nallow all", "hello;world", ".:--", "x".repeat(256)})
            assertFalse(UsbHashIdentity.valid(s), s);
    }
    @Test void acceptsRealHashCharacters() {
        assertTrue(UsbHashIdentity.valid("L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8="));
        assertTrue(UsbHashIdentity.valid("sha256:abcd1234"));
    }
}
