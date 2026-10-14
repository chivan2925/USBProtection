package com.group.usbshield.agent.notification;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.usbguard.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BlockNotificationPolicyTest {
 UsbDeviceInfo device(String state,String iface) {return new UsbDeviceInfo(11,state,"0951","1665","s","Kingston","hash",List.of(iface),iface.startsWith("08"),"");}
 UsbGuardPresenceEvent event(UsbGuardPresenceEvent.PresenceType type,String decision,String iface){return new UsbGuardPresenceEvent(type,decision,device(decision,iface));}
 private final ActiveUserInfo user=ActiveUserInfo.resolved("student01",1001L,"3","wayland","seat0");
 @Test void emitsOnceForBlockedStorageAndDeduplicates(){var rule=new BlockNotificationPolicy(1500);var e=event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50");assertTrue(rule.classify(e,user,Instant.ofEpochMilli(10_000)).isPresent());assertTrue(rule.classify(e,user,Instant.ofEpochMilli(10_001)).isEmpty());}
 @Test void ignoresNonStorageAllowedRemovalAndUnknownUser(){var rule=new BlockNotificationPolicy();var t=Instant.now();assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","03:01:02"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"allow","08:06:50"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.REMOVE,"block","08:06:50"),user,t).isEmpty());assertTrue(rule.classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50"),ActiveUserInfo.unknown("no-active"),t).isEmpty());}
 @Test void doesNotSendToTTY(){var tty=ActiveUserInfo.resolved("user",1000L,"3","tty","seat0");assertTrue(new BlockNotificationPolicy().classify(event(UsbGuardPresenceEvent.PresenceType.INSERT,"block","08:06:50"),tty,Instant.now()).isEmpty());}
}
