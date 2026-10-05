package com.group.usbshield.agent.usbguard;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UsbGuardEventParser {

    private static final Pattern HEADER =
            Pattern.compile("^(\\d+):\\s+(allow|block|reject)\\s+.*$");

    private static final Pattern VID_PID =
            Pattern.compile("\\bid\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})");

    private static final Pattern NAME =
            Pattern.compile("\\bname\\s+\"([^\"]*)\"");

    private static final Pattern SERIAL =
            Pattern.compile("\\bserial\\s+\"([^\"]*)\"");

    private static final Pattern HASH =
            Pattern.compile("\\bhash\\s+\"([^\"]*)\"");

    private static final Pattern INTERFACE =
            Pattern.compile("\\bwith-interface\\s+(?:\\{?\\s*([0-9a-fA-F:*]+)\\s*\\}?|(\\S+))");

    private static final Pattern MASS_STORAGE =
            Pattern.compile("(?i)(?:^|[\\s{])08:[0-9a-f*]{2}:[0-9a-f*]{2}(?:[\\s}]|$)");

    /**
     * Phân tích một dòng thiết bị (ví dụ từ usbguard list-devices hoặc device_rule từ watch).
     */
    public UsbDeviceInfo parseDeviceLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("device_rule=")) {
            trimmed = trimmed.substring("device_rule=".length()).trim();
        }

        String runtimeId = "";
        String rawDecision = "block"; // mặc định coi là block nếu không xác định

        Matcher headerMatcher = HEADER.matcher(trimmed);
        if (headerMatcher.matches()) {
            runtimeId = headerMatcher.group(1);
            rawDecision = headerMatcher.group(2);
        } else if (trimmed.startsWith("allow")) {
            rawDecision = "allow";
        } else if (trimmed.startsWith("block") || trimmed.startsWith("reject")) {
            rawDecision = "block";
        }

        String vid = findGroup(VID_PID, trimmed, 1);
        String pid = findGroup(VID_PID, trimmed, 2);
        String name = findGroup(NAME, trimmed, 1);
        String serial = findGroup(SERIAL, trimmed, 1);
        String hash = findGroup(HASH, trimmed, 1);

        String iface = findGroup(INTERFACE, trimmed, 1);
        if (iface == null || iface.isBlank()) {
            iface = findGroup(INTERFACE, trimmed, 2);
        }

        boolean isMassStorage = MASS_STORAGE.matcher(trimmed).find();
        String decision = "allow".equalsIgnoreCase(rawDecision) ? "ALLOWED" : "BLOCKED";

        return UsbDeviceInfo.builder()
                .runtimeId(runtimeId)
                .vendorId(vid != null ? vid.toLowerCase() : "")
                .productId(pid != null ? pid.toLowerCase() : "")
                .name(name != null ? name : "")
                .serial(serial != null ? serial : "")
                .hash(hash != null ? hash : "")
                .interfaceClass(iface != null ? iface : "")
                .massStorage(isMassStorage)
                .decision(decision)
                .build();
    }

    private String findGroup(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : "";
    }
}
