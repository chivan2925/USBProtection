package com.group.usbshield.agent.usbguard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class UsbGuardOutputParserTest {
 private final UsbGuardOutputParser parser=new UsbGuardOutputParser();
 @Test void parsesMassStorageAndMouseFromPhysicalWeek1Fixture() {
  var lines="14: block id 0951:1665 serial \"C81F660E8BE8FFA14601FEF6\" name \"DataTraveler 2.0\" hash \"xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=\" with-interface 08:06:50\n"+
   "10: allow id 093a:2510 serial \"\" name \"USB Optical Mouse\" with-interface 03:01:02";
  var devices=parser.parseDevices(lines);
  assertEquals(2,devices.size());assertTrue(devices.get(0).massStorage());assertEquals("0951",devices.get(0).vendorId());
  assertEquals("block",devices.get(0).state());assertFalse(devices.get(1).massStorage());
 }
 @Test void parsesBaseRuleWithoutMistaking08PatternForDeviceInterface(){
  var rules=parser.parseRules("1: allow with-interface none-of { 08:*:* }\n2: allow id 0951:1665 hash \"abc=\" label \"usbshield-managed\"");
  assertEquals(2,rules.size());assertTrue(rules.get(0).isBaseNonStorageRule());assertEquals("abc=",rules.get(1).hash());
 }
 @Test void ignoresUnrecognizedLines() {assertEquals(List.of(),parser.parseDevices("unexpected\n[IPC] denied"));}
}
