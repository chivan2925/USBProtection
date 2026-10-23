package com.group.usbshield.agent.usbguard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsbGuardRuleBuilder {
    public static final String MARKER = "usbshield-managed";
    private static final Pattern HEX4 = Pattern.compile("[a-fA-F0-9]{4}");
    private static final Pattern SAFE = Pattern.compile("[a-zA-Z0-9+/=_:.\\-]{1,255}");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?:id\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})|serial\\s+\"([^\"]*)\"|hash\\s+\"([^\"]*)\")(?=\\s|$)");

    public String allowRule(DeviceFingerprint id) {
        if (id == null) throw new IllegalArgumentException("fingerprint missing");
        boolean pair = id.vendorId() != null && !id.vendorId().isBlank();
        if (pair != (id.productId() != null && !id.productId().isBlank())) throw new IllegalArgumentException("VID/PID must be provided together");
        if (pair && (!HEX4.matcher(id.vendorId()).matches() || !HEX4.matcher(id.productId()).matches()))
            throw new IllegalArgumentException("VID/PID must be 4 hex characters");
        boolean serial = valid(id.serial());
        boolean hash = valid(id.hash());
        if (!serial && !hash) throw new IllegalArgumentException("Insufficient USB fingerprint: require hash or serial");
        if (serial && !pair) throw new IllegalArgumentException("serial needs VID/PID");
        StringBuilder rule = new StringBuilder("allow");
        if (pair) rule.append(" id ").append(id.vendorId().toLowerCase(Locale.ROOT)).append(':').append(id.productId().toLowerCase(Locale.ROOT));
        if (serial) rule.append(" serial \"").append(id.serial()).append('"');
        if (hash) rule.append(" hash \"").append(id.hash()).append('"');
        return rule.append(" label \"").append(MARKER).append('"').toString();
    }

    private static boolean valid(String value) {
        if (value == null || value.isBlank()) return false;
        if (!SAFE.matcher(value).matches()) throw new IllegalArgumentException("Unsafe fingerprint attribute");
        return true;
    }

    public String key(DeviceFingerprint id) {
        allowRule(id);
        if (id.hash() != null && !id.hash().isBlank()) return "HASH:" + id.hash();
        return "SERIAL:" + id.vendorId().toLowerCase(Locale.ROOT) + ':' + id.productId().toLowerCase(Locale.ROOT) + ':' + id.serial();
    }

    public DeviceFingerprint parseLegacy(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Empty whitelist entry");
        String input = raw.trim();
        if (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0 || input.indexOf(';') >= 0)
            throw new IllegalArgumentException("Malformed whitelist expression");
        if (input.startsWith("allow ")) input = input.substring(6).trim();
        if (SAFE.matcher(input).matches()) {
            DeviceFingerprint fingerprint = new DeviceFingerprint(null, null, null, input);
            allowRule(fingerprint);
            return fingerprint;
        }
        String vendor = null;
        String product = null;
        String serial = null;
        String hash = null;
        int position = 0;
        while (position < input.length()) {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
            if (position == input.length()) break;
            Matcher attribute = ATTRIBUTE.matcher(input);
            attribute.region(position, input.length());
            if (!attribute.lookingAt()) throw new IllegalArgumentException("Malformed whitelist expression");
            if (attribute.group(1) != null) {
                if (vendor != null) throw new IllegalArgumentException("Duplicate id attribute");
                vendor = attribute.group(1);
                product = attribute.group(2);
            } else if (attribute.group(3) != null) {
                if (serial != null) throw new IllegalArgumentException("Duplicate serial attribute");
                serial = attribute.group(3);
            } else {
                if (hash != null) throw new IllegalArgumentException("Duplicate hash attribute");
                hash = attribute.group(4);
            }
            position = attribute.end();
        }
        DeviceFingerprint fingerprint = new DeviceFingerprint(vendor, product, serial, hash);
        allowRule(fingerprint);
        return fingerprint;
    }
}
