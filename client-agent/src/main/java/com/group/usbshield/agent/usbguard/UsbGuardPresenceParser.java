package com.group.usbshield.agent.usbguard;

import java.util.Optional;
import java.util.regex.Pattern;

public class UsbGuardPresenceParser {
    private static final Pattern HEADER = Pattern.compile("^\\[device\\] PresenceChanged: id=(\\d+)(?:\\s.*)?$");
    private final UsbGuardEventParser deviceParser;

    public UsbGuardPresenceParser(UsbGuardEventParser parser) {
        this.deviceParser = parser;
    }

    public Optional<UsbGuardPresenceEvent> parse(String section) {
        if (section == null || section.isBlank()) return Optional.empty();
        var lines = section.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (lines.isEmpty()) return Optional.empty();
        var header = HEADER.matcher(lines.getFirst());
        if (!header.matches()) return Optional.empty();
        int deviceId;
        try {
            deviceId = Integer.parseInt(header.group(1));
        } catch (NumberFormatException invalidId) {
            return Optional.empty();
        }
        String event = null;
        String target = null;
        String rule = null;
        for (String line : lines) {
            if (line.startsWith("[device]") && !line.equals(lines.getFirst())) return Optional.empty();
            if (line.startsWith("event=")) {
                if (event != null) return Optional.empty();
                event = line.substring(6);
            } else if (line.startsWith("target=")) {
                if (target != null) return Optional.empty();
                target = line.substring(7);
            } else if (line.startsWith("device_rule=")) {
                if (rule != null) return Optional.empty();
                rule = line;
            }
        }
        if (event == null || rule == null) return Optional.empty();
        UsbGuardPresenceEvent.PresenceType type = switch (event) {
            case "Insert" -> UsbGuardPresenceEvent.PresenceType.INSERT;
            case "Remove" -> UsbGuardPresenceEvent.PresenceType.REMOVE;
            default -> null;
        };
        if (type == null) return Optional.empty();
        UsbDeviceInfo info = deviceParser.parseDeviceLine(rule);
        if (info == null) return Optional.empty();
        String resolvedTarget = target == null ? info.state() : target;
        if (!resolvedTarget.equals("allow") && !resolvedTarget.equals("block") && !resolvedTarget.equals("reject")) return Optional.empty();
        info = new UsbDeviceInfo(deviceId, info.state(), info.vendorId(), info.productId(),
                info.serial(), info.name(), info.hash(), info.interfaces(), info.massStorage(), info.rawRule());
        return Optional.of(new UsbGuardPresenceEvent(type, resolvedTarget, info));
    }
}
