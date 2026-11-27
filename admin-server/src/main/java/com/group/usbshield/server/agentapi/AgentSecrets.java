package com.group.usbshield.server.agentapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class AgentSecrets {
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentSecrets() {}

    static String issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static boolean valid(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static boolean matches(String digest, String value) {
        return valid(digest) && valid(value) && MessageDigest.isEqual(
                digest.getBytes(StandardCharsets.US_ASCII), digest(value).getBytes(StandardCharsets.US_ASCII));
    }
}
