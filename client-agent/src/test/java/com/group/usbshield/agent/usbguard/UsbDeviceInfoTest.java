package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UsbDeviceInfoTest {
    @Test
    void recordSnapshotsInterfacesAndPreservesWeek1Contract() {
        List<String> source = new ArrayList<>(List.of("08:06:50", "03:01:02"));
        UsbDeviceInfo device = new UsbDeviceInfo(10, "block", "0951", "1665", "abc",
                "DataTraveler", "hash", source, true, "10: block id 0951:1665");
        source.add("ff:00:00");
        assertEquals(List.of("08:06:50", "03:01:02"), device.interfaces());
        assertThrows(UnsupportedOperationException.class, () -> device.interfaces().add("00:00:00"));
        assertEquals("BLOCKED", device.decision());
        assertEquals("08:06:50", device.toContractDeviceMap().get("interface"));
        assertEquals("0951", device.toContractDeviceMap().get("vendorId"));
    }

    @Test
    void allowAndRejectAreMappedWithoutLosingRawTarget() {
        UsbDeviceInfo allowed = new UsbDeviceInfo(-1, "allow", "093a", "2510", "", "Mouse", "",
                List.of("03:01:02"), false, "allow id 093a:2510");
        UsbDeviceInfo rejected = new UsbDeviceInfo(-1, "reject", "0951", "1665", "", "Flash", "",
                List.of("08:06:50"), true, "reject id 0951:1665");
        assertEquals("ALLOWED", allowed.decision());
        assertEquals("BLOCKED", rejected.decision());
        assertEquals("reject", rejected.state());
    }
}
