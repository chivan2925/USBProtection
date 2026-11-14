package com.group.usbshield.server.auth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdminPasswordPolicyTest {
    @Test void rejectsWeakOrUnprintable() {
        assertFalse(AdminPasswordPolicy.acceptable(null));
        assertFalse(AdminPasswordPolicy.acceptable("short"));
        assertFalse(AdminPasswordPolicy.acceptable(" ".repeat(15)));
        assertFalse(AdminPasswordPolicy.acceptable("x".repeat(129)));
        assertFalse(AdminPasswordPolicy.acceptable("é".repeat(40))); // 80 UTF-8 bytes
        assertFalse(AdminPasswordPolicy.acceptable("validPassword\n"));
        assertTrue(AdminPasswordPolicy.acceptable("correct horse battery staple"));
    }
}
