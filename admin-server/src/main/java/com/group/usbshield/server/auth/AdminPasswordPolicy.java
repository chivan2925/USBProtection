package com.group.usbshield.server.auth;

/** Shared, explicit password policy for authenticated change and offline recovery. */
public final class AdminPasswordPolicy {
    private AdminPasswordPolicy() {}
    public static boolean acceptable(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12 ||
                !withinEncoderLimit(password)) return false;
        return password.codePoints().noneMatch(Character::isISOControl);
    }

    static boolean withinEncoderLimit(String password) {
        return password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
