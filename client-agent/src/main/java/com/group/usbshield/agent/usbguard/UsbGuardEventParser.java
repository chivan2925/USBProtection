package com.group.usbshield.agent.usbguard;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UsbGuardEventParser {
    private static final Pattern HEADER = Pattern.compile("^(\\d+):\\s+(allow|block|reject)(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET = Pattern.compile("^(allow|block|reject)(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VID_PID = Pattern.compile("(?:^|\\s)id\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})(?=\\s|$)");
    private static final Pattern NAME = Pattern.compile("(?:^|\\s)name\\s+\"([^\"]*)\"");
    private static final Pattern SERIAL = Pattern.compile("(?:^|\\s)serial\\s+\"([^\"]*)\"");
    private static final Pattern HASH = Pattern.compile("(?:^|\\s)hash\\s+\"([^\"]*)\"");
    private static final Pattern INTERFACE = Pattern.compile("(?i)(?<![0-9a-z:*])(?:[0-9a-f]{2}|\\*):(?:[0-9a-f]{2}|\\*):(?:[0-9a-f]{2}|\\*)(?![0-9a-z:*])");

    public UsbDeviceInfo parseDeviceLine(String line) {
        if (line == null || line.isBlank()) return null;
        String raw = line.trim();
        if (raw.startsWith("device_rule=")) raw = raw.substring("device_rule=".length()).trim();
        int runtimeId = -1;
        String state;
        Matcher header = HEADER.matcher(raw);
        if (header.matches()) {
            try {
                runtimeId = Integer.parseInt(header.group(1));
            } catch (NumberFormatException invalidId) {
                return null;
            }
            state = header.group(2).toLowerCase(Locale.ROOT);
        } else {
            Matcher target = TARGET.matcher(raw);
            if (!target.find()) return null;
            state = target.group(1).toLowerCase(Locale.ROOT);
        }

        String interfacesText = extractInterfacesSection(raw);
        Matcher interfaceMatcher = INTERFACE.matcher(interfacesText);
        List<String> interfaces = new ArrayList<>();
        boolean massStorage = false;
        while (interfaceMatcher.find()) {
            String iface = interfaceMatcher.group().toLowerCase(Locale.ROOT);
            interfaces.add(iface);
            if (iface.startsWith("08:")) massStorage = true;
        }
        return new UsbDeviceInfo(runtimeId, state,
                find(VID_PID, raw, 1).toLowerCase(Locale.ROOT),
                find(VID_PID, raw, 2).toLowerCase(Locale.ROOT),
                find(SERIAL, raw, 1), find(NAME, raw, 1), find(HASH, raw, 1),
                interfaces, massStorage, raw);
    }

    private String extractInterfacesSection(String rule) {
        int start = findUnquotedToken(rule, "with-interface", 0);
        if (start < 0) return "";
        start += "with-interface".length();
        int end = findUnquotedToken(rule, "with-connect-type", start);
        if (end < 0) end = rule.length();
        String section = rule.substring(start, end).trim();
        if (section.startsWith("none-of")) return "";
        return section;
    }

    private int findUnquotedToken(String input, String token, int from) {
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"' && (i == 0 || input.charAt(i - 1) != '\\')) quoted = !quoted;
            if (i < from || quoted || !input.startsWith(token, i)) continue;
            if (i > 0 && !Character.isWhitespace(input.charAt(i - 1))) continue;
            int end = i + token.length();
            if (end == input.length() || Character.isWhitespace(input.charAt(end))) return i;
        }
        return -1;
    }

    private String find(Pattern pattern, String line, int group) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(group) : "";
    }
}
