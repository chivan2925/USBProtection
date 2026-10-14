package com.group.usbshield.agent.usbguard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UsbGuardPresenceParserTest {
 private final UsbGuardPresenceParser parser=new UsbGuardPresenceParser(new UsbGuardEventParser());
 private final String raw="[device] PresenceChanged: id=11\nevent=Insert\ntarget=block\ndevice_rule=block id 0951:1665 name \"Kingston\" hash \"abc=\" with-interface 08:06:50\n";
 @Test void emitsInsertOnce(){var e=parser.parse(raw).orElseThrow();assertEquals(UsbGuardPresenceEvent.PresenceType.INSERT,e.presenceType());assertTrue(e.device().massStorage());assertEquals("block",e.target());}
 @Test void skipsPolicyChangedAndApplied(){assertTrue(parser.parse(raw.replace("PresenceChanged","PolicyChanged")).isEmpty());assertTrue(parser.parse(raw.replace("PresenceChanged","PolicyApplied")).isEmpty());}
 @Test void parsesRemove(){assertEquals(UsbGuardPresenceEvent.PresenceType.REMOVE,parser.parse(raw.replace("event=Insert","event=Remove")).orElseThrow().presenceType());}
}
