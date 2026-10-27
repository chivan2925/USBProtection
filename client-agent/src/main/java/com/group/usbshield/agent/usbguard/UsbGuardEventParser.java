package com.group.usbshield.agent.usbguard;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Member 2 — Week 2: parse USBGuard device lines into the immutable record. */
@Component
public class UsbGuardEventParser {

    private static final Pattern HEADER = Pattern.compile("^(\\d+):\\s+(allow|block|reject)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VID_PID = Pattern.compile("\\bid\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})");
    private static final Pattern NAME = Pattern.compile("\\bname\\s+\"([^\"]*)\"");
    private static final Pattern SERIAL = Pattern.compile("\\bserial\\s+\"([^\"]*)\"");
    private static final Pattern HASH = Pattern.compile("\\bhash\\s+\"([^\"]*)\"");
    private static final Pattern INTERFACE = Pattern.compile("(?i)([0-9a-f*]{2}:[0-9a-f*]{2}:[0-9a-f*]{2})");

    /** Supports list-devices and watch's device_rule= line (without numeric ID). */
    public UsbDeviceInfo parseDeviceLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("device_rule=")) {
            trimmed = trimmed.substring("device_rule=".length()).trim();
        }

        int runtimeId = -1;
        String state;
        Matcher header = HEADER.matcher(trimmed);
        if (header.matches()) {
            runtimeId = Integer.parseInt(header.group(1));
            state = header.group(2).toLowerCase(java.util.Locale.ROOT);
        } else if (trimmed.startsWith("allow ") || "allow".equals(trimmed)) {
            state = "allow";
        } else if (trimmed.startsWith("block ") || "block".equals(trimmed)) {
            state = "block";
        } else if (trimmed.startsWith("reject ") || "reject".equals(trimmed)) {
            state = "reject";
        } else {
            return null; // Never turn unrecognized lines into BLOCKED events.
        }

        String vendorId = find(VID_PID, trimmed, 1).toLowerCase(java.util.Locale.ROOT);
        String productId = find(VID_PID, trimmed, 2).toLowerCase(java.util.Locale.ROOT);
        String interfacesText = extractInterfacesSection(trimmed);
        Matcher interfaceMatcher = INTERFACE.matcher(interfacesText);
        List<String> interfaces = new ArrayList<>();
        boolean massStorage = false;
        while (interfaceMatcher.find()) {
            String iface = interfaceMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
            interfaces.add(iface);
            massStorage |= iface.startsWith("08:");
        }

        return new UsbDeviceInfo(runtimeId, state, vendorId, productId,
                find(SERIAL, trimmed, 1), find(NAME, trimmed, 1),
                find(HASH, trimmed, 1), interfaces, massStorage, trimmed);
    }

    private String extractInterfacesSection(String rule) {
        int start = rule.indexOf("with-interface");
        if (start < 0) return "";
        String section = rule.substring(start + "with-interface".length());
        // Stop at the next known USBGuard attribute, e.g. with-connect-type.
        int end = section.indexOf(" with-");
        return end >= 0 ? section.substring(0, end) : section;
    }

    private String find(Pattern pattern, String line, int group) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(group) : "";
    }
}
