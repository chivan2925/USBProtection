package com.group.usbshield.agent.usbguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Member 2 — Week 2: USBGuard list-devices and list-rules parsers. */
public class UsbGuardOutputParser {
    private static final Pattern RULE_HEADER=Pattern.compile("^(\\d+):\\s+(allow|block|reject)(?:\\s+.*)?$",Pattern.CASE_INSENSITIVE);
    private final UsbGuardEventParser deviceParser = new UsbGuardEventParser();
    public List<UsbDeviceInfo> parseDevices(String output) {
        List<UsbDeviceInfo> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.lines().toList()) {
            try {
                UsbDeviceInfo device = deviceParser.parseDeviceLine(line);
                if (device != null && device.runtimeId() >= 0) result.add(device);
            } catch (IllegalArgumentException ignored) { /* ignore malformed unrelated line */ }
        }
        return List.copyOf(result);
    }
    public List<UsbGuardRuleInfo> parseRules(String output) {
        List<UsbGuardRuleInfo> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.lines().toList()) {
            String raw = line.trim();
            var header=RULE_HEADER.matcher(raw);
            if(!header.matches()) continue;
            try {
                long ruleId=Long.parseLong(header.group(1));
                UsbDeviceInfo info=deviceParser.parseDeviceLine(raw.substring(raw.indexOf(':')+1).trim());
                if(info==null)continue;
                result.add(new UsbGuardRuleInfo(ruleId, info.state().toLowerCase(Locale.ROOT),
                        info.vendorId(), info.productId(), info.serial(), info.hash(), raw));
            } catch (IllegalArgumentException ignored) { /* malformed rule: skip, never mutate it */ }
        }
        return List.copyOf(result);
    }
}
