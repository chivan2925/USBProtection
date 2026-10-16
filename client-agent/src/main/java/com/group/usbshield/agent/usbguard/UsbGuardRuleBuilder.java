package com.group.usbshield.agent.usbguard;

import java.util.Locale;
import java.util.regex.Pattern;

/** Member 2 — Week 2: strictly validate USBGuard rule attributes before any policy mutation. */
public class UsbGuardRuleBuilder {
    public static final String MARKER = "usbshield-managed";
    private static final Pattern HEX4 = Pattern.compile("[a-fA-F0-9]{4}");
    private static final Pattern SAFE = Pattern.compile("[a-zA-Z0-9+/=_:.\\-]{1,255}");
    public String allowRule(DeviceFingerprint id) {
        if (id == null) throw new IllegalArgumentException("fingerprint missing");
        boolean pair = id.vendorId() != null && !id.vendorId().isBlank();
        if (pair != (id.productId() != null && !id.productId().isBlank())) throw new IllegalArgumentException("VID/PID must be provided together");
        if (pair && (!HEX4.matcher(id.vendorId()).matches() || !HEX4.matcher(id.productId()).matches()))
            throw new IllegalArgumentException("VID/PID must be 4 hex characters");
        boolean serial = valid(id.serial()); boolean hash = valid(id.hash());
        if (!serial && !hash) throw new IllegalArgumentException("Insufficient USB fingerprint: require hash or serial");
        if (serial && !pair) throw new IllegalArgumentException("serial needs VID/PID");
        StringBuilder b = new StringBuilder("allow");
        if (pair) b.append(" id ").append(id.vendorId().toLowerCase(Locale.ROOT)).append(':').append(id.productId().toLowerCase(Locale.ROOT));
        if (serial) b.append(" serial \"").append(id.serial()).append('"');
        if (hash) b.append(" hash \"").append(id.hash()).append('"');
        return b.append(" label \"").append(MARKER).append('"').toString();
    }
    private static boolean valid(String s) {
        if (s == null || s.isBlank()) return false;
        if (!SAFE.matcher(s).matches()) throw new IllegalArgumentException("Unsafe fingerprint attribute");
        return true;
    }
    public String key(DeviceFingerprint id) {
        allowRule(id); // validates
        if (id.hash() != null && !id.hash().isBlank()) return "HASH:" + id.hash();
        return "SERIAL:" + id.vendorId().toLowerCase(Locale.ROOT) + ':' + id.productId().toLowerCase(Locale.ROOT) + ':' + id.serial();
    }
    /** Input from M3's legacy PolicySyncService (fingerprintValue, not a full typed DTO). */
    public DeviceFingerprint parseLegacy(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Empty whitelist entry");
        String input = raw.trim();
        if (input.startsWith("allow ")) input = input.substring(6);
        var id = java.util.regex.Pattern.compile("(?:^|\\s)id\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})(?=\\s|$)").matcher(input);
        var serial = java.util.regex.Pattern.compile("(?:^|\\s)serial\\s+\"([^\"]+)\"").matcher(input);
        var hash = java.util.regex.Pattern.compile("(?:^|\\s)hash\\s+\"([^\"]+)\"").matcher(input);
        String vid = id.find() ? id.group(1) : null, pid = vid == null ? null : id.group(2);
        String ser = serial.find() ? serial.group(1) : null, h = hash.find() ? hash.group(1) : null;
        if (vid == null && ser == null && h == null && SAFE.matcher(input).matches()) h = input;
        DeviceFingerprint parsed = new DeviceFingerprint(vid, pid, ser, h);
        allowRule(parsed);
        // Reject arbitrary appended USBGuard expressions or malformed fragments.
        if (input.contains("\n") || input.contains("\r") || input.contains(";") || input.contains(" label ")) throw new IllegalArgumentException("Malformed input");
        return parsed;
    }
}
