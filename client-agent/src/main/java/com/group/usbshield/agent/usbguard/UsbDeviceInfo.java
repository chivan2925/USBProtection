package com.group.usbshield.agent.usbguard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Member 2 — Week 2: immutable USBGuard inspection result (guide section 8).
 *
 * <p>{@code runtimeId == -1} means a watch {@code device_rule=} entry without a
 * numeric device ID. USBGuard's raw target is preserved as allow/block/reject;
 * the Agent wire decision is derived rather than stored redundantly.</p>
 *
 * <p>M3 owns HTTP serialization and transport; {@link #toContractDeviceMap()}
 * maintains the existing Week 1 device JSON shape.</p>
 */
public record UsbDeviceInfo(
        int runtimeId,
        String state,
        String vendorId,
        String productId,
        String serial,
        String name,
        String hash,
        List<String> interfaces,
        boolean massStorage,
        String rawRule
) {
    public UsbDeviceInfo {
        if (runtimeId < -1) {
            throw new IllegalArgumentException("runtimeId must be >= -1");
        }
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
    }

    /** Derived Week 1 Agent decision; block and reject both mean BLOCKED. */
    public String decision() {
        if ("allow".equalsIgnoreCase(state)) {
            return "ALLOWED";
        }
        if ("block".equalsIgnoreCase(state) || "reject".equalsIgnoreCase(state)) {
            return "BLOCKED";
        }
        return "UNKNOWN";
    }

    /** Legacy wire protocol uses a single interface string; no array on the wire yet. */
    public String interfaceClass() {
        return interfaces.isEmpty() ? "" : interfaces.getFirst();
    }

    /** Preserve the Agent event device JSON fields agreed in Week 1. */
    public Map<String, Object> toContractDeviceMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("vendorId", vendorId != null ? vendorId : "");
        map.put("productId", productId != null ? productId : "");
        map.put("serial", serial != null ? serial : "");
        map.put("name", name != null ? name : "");
        map.put("hash", hash != null ? hash : "");
        map.put("interface", interfaceClass());
        return map;
    }
}
