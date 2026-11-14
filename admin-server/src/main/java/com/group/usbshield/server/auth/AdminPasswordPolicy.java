package com.group.usbshield.server.auth;

/** Shared, explicit password policy for authenticated change and offline recovery. */
public final class AdminPasswordPolicy {
    private AdminPasswordPolicy() {}
    public static boolean acceptable(String password) {
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 128 ||
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return false;
        return password.codePoints().noneMatch(Character::isISOControl);
    }
}
