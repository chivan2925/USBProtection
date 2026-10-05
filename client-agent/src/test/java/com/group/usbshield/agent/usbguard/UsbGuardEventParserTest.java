package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsbGuardEventParserTest {

    private UsbGuardEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new UsbGuardEventParser();
    }

    @Test
    @DisplayName("Bóc tách chuột HID class 03 - Phải ALLOWED và massStorage=false")
    void testParseHidMouseDevice() {
        // Log thật từ 12-hid-mouse-test.txt của Member 2
        String line = "10: allow id 093a:2510 serial \"\" name \"USB Optical Mouse\" hash \"L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=\" parent-hash \"Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=\" via-port \"3-2\" with-interface 03:01:02 with-connect-type \"unknown\"";

        UsbDeviceInfo info = parser.parseDeviceLine(line);

        assertNotNull(info);
        assertEquals("10", info.getRuntimeId());
        assertEquals("093a", info.getVendorId());
        assertEquals("2510", info.getProductId());
        assertEquals("USB Optical Mouse", info.getName());
        assertEquals("", info.getSerial());
        assertEquals("03:01:02", info.getInterfaceClass());
        assertFalse(info.isMassStorage(), "Chuột không phải là Mass Storage");
        assertEquals("ALLOWED", info.getDecision());
    }

    @Test
    @DisplayName("Bóc tách USB Flash Disk Kingston class 08 - Phải BLOCKED và massStorage=true")
    void testParseBlockedDataTravelerFlashDisk() {
        // Log thật từ 05-blocked-devices.txt của Member 2
        String line = "10: block id 0951:1665 serial \"C81F660E8BE8FFA14601FEF6\" name \"DataTraveler 2.0\" hash \"xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=\" parent-hash \"Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=\" via-port \"3-2\" with-interface 08:06:50 with-connect-type \"unknown\"";

        UsbDeviceInfo info = parser.parseDeviceLine(line);

        assertNotNull(info);
        assertEquals("10", info.getRuntimeId());
        assertEquals("0951", info.getVendorId());
        assertEquals("1665", info.getProductId());
        assertEquals("DataTraveler 2.0", info.getName());
        assertEquals("C81F660E8BE8FFA14601FEF6", info.getSerial());
        assertEquals("08:06:50", info.getInterfaceClass());
        assertTrue(info.isMassStorage(), "Phải nhận diện đúng Mass Storage Class 08");
        assertEquals("BLOCKED", info.getDecision());
    }

    @Test
    @DisplayName("Bóc tách dòng device_rule từ luồng usbguard watch")
    void testParseDeviceRuleFromWatchStream() {
        // Trích xuất từ 13-usbguard-watch-full.txt
        String watchLine = "device_rule=block id 0951:1665 serial \"C81F660E8BE8FFA14601FEF6\" name \"DataTraveler 2.0\" hash \"xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=\" parent-hash \"Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=\" via-port \"3-2\" with-interface 08:06:50 with-connect-type \"unknown\"";

        UsbDeviceInfo info = parser.parseDeviceLine(watchLine);

        assertNotNull(info);
        assertEquals("0951", info.getVendorId());
        assertEquals("1665", info.getProductId());
        assertEquals("DataTraveler 2.0", info.getName());
        assertTrue(info.isMassStorage());
        assertEquals("BLOCKED", info.getDecision());

        var contractMap = info.toContractDeviceMap();
        assertEquals("0951", contractMap.get("vendorId"));
        assertEquals("1665", contractMap.get("productId"));
        assertEquals("DataTraveler 2.0", contractMap.get("name"));
        assertEquals("08:06:50", contractMap.get("interface"));
    }
}
