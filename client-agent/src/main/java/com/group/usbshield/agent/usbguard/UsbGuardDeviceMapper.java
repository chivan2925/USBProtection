package com.group.usbshield.agent.usbguard;

import java.util.Map;

/** Member 2 — Week 2: translates immutable USB inspection to the frozen M1/M3 device contract. */
public final class UsbGuardDeviceMapper {
    public Map<String,Object> toWireDevice(UsbDeviceInfo device) {
        if(device==null)throw new IllegalArgumentException("device");
        return device.toContractDeviceMap();
    }
}
