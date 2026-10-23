package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsbGuardWeek3ParsingAndIdentityTest {
    private final UsbGuardEventParser deviceParser = new UsbGuardEventParser();
    private final UsbGuardOutputParser outputParser = new UsbGuardOutputParser();
    private final UsbGuardPresenceParser presenceParser = new UsbGuardPresenceParser(deviceParser);
    private final UsbGuardRuleBuilder builder = new UsbGuardRuleBuilder();

    @Test
    void seriallessMassStorageRemainsBlockedAndRetainsHash() {
        UsbDeviceInfo device = deviceParser.parseDeviceLine(
                "7: block id 0951:1665 name \"No Serial USB\" hash \"Abc+/123=\" with-interface 08:06:50");
        assertNotNull(device);
        assertEquals("", device.serial());
        assertEquals("Abc+/123=", device.hash());
        assertTrue(device.massStorage());
        assertEquals("BLOCKED", device.decision());
        assertEquals("", device.toContractDeviceMap().get("serial"));
    }

    @Test
    void rejectsIdentityBasedOnlyOnVidPidOrDeviceName() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.allowRule(new DeviceFingerprint("0951", "1665", "", "")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.parseLegacy("id 0951:1665"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.parseLegacy("name \"Kingston\""));
    }

    @Test
    void hashOnlyAndSeriallessHashRulesAreValid() {
        assertEquals("allow hash \"Abc+/123=\" label \"usbshield-managed\"",
                builder.allowRule(builder.parseLegacy("Abc+/123=")));
        DeviceFingerprint noSerial = builder.parseLegacy("id 0951:1665 hash \"Abc+/123=\"");
        assertNull(noSerial.serial());
        assertEquals("HASH:Abc+/123=", builder.key(noSerial));
        assertEquals("allow id 0951:1665 hash \"Abc+/123=\" label \"usbshield-managed\"",
                builder.allowRule(noSerial));
    }

    @Test
    void rejectsAppendedOrDuplicateLegacyAttributes() {
        for (String invalid : List.of(
                "hash \"good\" with-interface 08:06:50",
                "hash \"good\" label \"evil\"",
                "hash \"good\" hash \"second\"",
                "id 0951:1665 id 1234:5678 serial \"A\"",
                "serial \"A\"",
                "hash \"good\"; echo bad",
                "allow hash \"ok\" trailing")) {
            assertThrows(IllegalArgumentException.class, () -> builder.parseLegacy(invalid), invalid);
        }
    }

    @Test
    void preservesSafeExplicitSerialAndAllowsAttributeReordering() {
        DeviceFingerprint id = builder.parseLegacy("hash \"abc=\" id 0951:1665 serial \"S1\"");
        assertEquals("0951", id.vendorId());
        assertEquals("S1", id.serial());
        assertEquals("abc=", id.hash());
        assertEquals("allow id 0951:1665 serial \"S1\" hash \"abc=\" label \"usbshield-managed\"", builder.allowRule(id));
    }

    @Test
    void ignoresInterfaceTextInsideDeviceNameAndNegativeBaseRules() {
        UsbDeviceInfo named = deviceParser.parseDeviceLine(
                "8: allow id 1234:5678 name \"with-interface 08:06:50\" with-interface 03:01:02");
        assertNotNull(named);
        assertFalse(named.massStorage());
        assertEquals("03:01:02", named.interfaceClass());
        assertEquals(1, outputParser.parseRules("1: allow with-interface none-of { 08:*:* }").size());
        assertTrue(outputParser.parseRules("1: allow with-interface none-of { 08:*:* }").getFirst().isBaseNonStorageRule());
    }

    @Test
    void parsesCompositeUsbWithMassStorageButNotNonStorageOnly() {
        UsbDeviceInfo composite = deviceParser.parseDeviceLine(
                "9: block id 1111:2222 with-interface { 03:01:02 08:06:50 } with-connect-type \"unknown\"");
        assertNotNull(composite);
        assertEquals(List.of("03:01:02", "08:06:50"), composite.interfaces());
        assertTrue(composite.massStorage());
    }

    @Test
    void malformedNumericDeviceIdsAreSkippedInsteadOfCrashing() {
        String huge = "999999999999999999999: block id 0951:1665 with-interface 08:06:50";
        assertNull(deviceParser.parseDeviceLine(huge));
        assertTrue(outputParser.parseDevices(huge).isEmpty());
        assertEquals(1, outputParser.parseDevices(huge + "\n4: allow id 093a:2510 with-interface 03:01:02").size());
        assertTrue(presenceParser.parse("[device] PresenceChanged: id=99999999999999\nevent=Insert\ntarget=block\ndevice_rule=block id 0951:1665 with-interface 08:06:50").isEmpty());
    }

    @Test
    void presenceRejectsDuplicateFieldsAndInvalidTargets() {
        String valid = "[device] PresenceChanged: id=4\nevent=Insert\ntarget=block\ndevice_rule=block id 0951:1665 hash \"abc=\" with-interface 08:06:50";
        assertTrue(presenceParser.parse(valid).isPresent());
        assertTrue(presenceParser.parse(valid.replace("target=block", "target=grant")).isEmpty());
        assertTrue(presenceParser.parse(valid.replace("event=Insert", "event=Insert\nevent=Remove")).isEmpty());
        assertTrue(presenceParser.parse(valid.replace("device_rule=", "device_rule=block id 1111:2222\ndevice_rule=")).isEmpty());
        assertTrue(presenceParser.parse(valid.replace("PresenceChanged", "PolicyChanged")).isEmpty());
    }
}
