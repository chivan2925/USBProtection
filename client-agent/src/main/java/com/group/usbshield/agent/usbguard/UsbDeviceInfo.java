package com.group.usbshield.agent.usbguard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsbDeviceInfo {
    private String runtimeId;
    private String vendorId;
    private String productId;
    private String serial;
    private String name;
    private String hash;
    private String interfaceClass;
    private boolean massStorage;
    private String decision; // ALLOWED, BLOCKED

    public Map<String, Object> toContractDeviceMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("vendorId", vendorId != null ? vendorId : "");
        map.put("productId", productId != null ? productId : "");
        map.put("serial", serial != null ? serial : "");
        map.put("name", name != null ? name : "");
        map.put("hash", hash != null ? hash : "");
        map.put("interface", interfaceClass != null ? interfaceClass : "");
        return map;
    }
}
