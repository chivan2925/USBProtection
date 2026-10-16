package com.group.usbshield.agent.usbguard;

import java.util.Optional;
import java.util.regex.Pattern;

/** Member 2 — Week 2: parse a complete USBGuard watch section. */
public class UsbGuardPresenceParser {
    private static final Pattern HEADER = Pattern.compile("^\\[device\\] PresenceChanged: id=(\\d+).*$");
    private final UsbGuardEventParser deviceParser;
    public UsbGuardPresenceParser(UsbGuardEventParser parser) { this.deviceParser=parser; }
    public Optional<UsbGuardPresenceEvent> parse(String section) {
        if (section == null || section.isBlank()) return Optional.empty();
        var lines = section.lines().map(String::trim).filter(s->!s.isEmpty()).toList();
        if (lines.isEmpty()) return Optional.empty();
        var match = HEADER.matcher(lines.getFirst());
        if (!match.matches()) return Optional.empty();
        String ev=null,target=null,rule=null;
        for (String line : lines) {
            if (line.startsWith("event=")) ev=line.substring(6);
            else if (line.startsWith("target=")) target=line.substring(7);
            else if (line.startsWith("device_rule=")) rule=line;
        }
        if (ev == null || rule == null) return Optional.empty();
        UsbGuardPresenceEvent.PresenceType type = switch(ev) { case "Insert" -> UsbGuardPresenceEvent.PresenceType.INSERT; case "Remove" -> UsbGuardPresenceEvent.PresenceType.REMOVE; default -> null; };
        if (type == null) return Optional.empty();
        UsbDeviceInfo info = deviceParser.parseDeviceLine(rule);
        if (info == null) return Optional.empty();
        info = new UsbDeviceInfo(Integer.parseInt(match.group(1)),info.state(),info.vendorId(),
                info.productId(),info.serial(),info.name(),info.hash(),info.interfaces(),info.massStorage(),info.rawRule());
        return Optional.of(new UsbGuardPresenceEvent(type, target == null ? info.state() : target, info));
    }
}
