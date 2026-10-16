package com.group.usbshield.agent.event;
import com.group.usbshield.agent.session.*;
import com.group.usbshield.agent.usbguard.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class UsbEventEnricherTest {
 private final UsbDeviceInfo device=new UsbDeviceInfo(11,"block","0951","1665","","Kingston","hash",List.of("08:06:50"),true,"");
 @Test void capturesUserAndInstantAtEventTime(){var clock=Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"),ZoneOffset.UTC);var service=new UsbEventEnricher(()->ActiveUserInfo.resolved("usbdev",1000L,"21","wayland","seat0"),clock);var data=service.enrich("EP-A","lab",new UsbGuardPresenceEvent(UsbGuardPresenceEvent.PresenceType.INSERT,"block",device));assertEquals("usbdev",data.linuxUsername());assertEquals("BLOCKED",data.decision());assertEquals(Instant.parse("2026-10-14T08:00:00Z"),data.occurredAt());}
 @Test void unknownDoesNotDropEvent(){var data=new UsbEventEnricher(()->ActiveUserInfo.unknown("no-session"),Clock.systemUTC()).enrich("EP-A","lab",new UsbGuardPresenceEvent(UsbGuardPresenceEvent.PresenceType.REMOVE,"block",device));assertEquals("UNKNOWN",data.linuxUsername());assertEquals("DISCONNECTED",data.eventType());}
}
