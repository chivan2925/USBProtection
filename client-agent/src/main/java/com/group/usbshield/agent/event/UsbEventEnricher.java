package com.group.usbshield.agent.event;

import com.group.usbshield.agent.session.*;
import com.group.usbshield.agent.usbguard.*;
import java.time.Clock;
import java.time.Instant;

/** Member 2 — Week 2: snapshot attribution at event time, independently of M3 HTTP transport. */
public class UsbEventEnricher {
    private final ActiveUserResolver resolver;
    private final Clock clock;
    public UsbEventEnricher(ActiveUserResolver resolver, Clock clock) {this.resolver=resolver;this.clock=clock;}
    public ClientUsbEvent enrich(String endpointId,String hostname,UsbGuardPresenceEvent event) {
        if(event==null||event.device()==null)throw new IllegalArgumentException("Presence event missing");
        ActiveUserInfo user;
        try {user=resolver.resolveCurrentActiveUser();} catch (RuntimeException e) {user=ActiveUserInfo.unknown("loginctl-error");}
        if(user==null)user=ActiveUserInfo.unknown("resolver-null");
        return new ClientUsbEvent(endpointId,hostname,user.username(),user.uid(),user.sessionId(),user.sessionType(),user.seat(),
                event.device().toContractDeviceMap(),
                event.presenceType()==UsbGuardPresenceEvent.PresenceType.INSERT?"CONNECTED":"DISCONNECTED",
                "allow".equalsIgnoreCase(event.target())?"ALLOWED":"BLOCKED",Instant.now(clock));
    }
}
