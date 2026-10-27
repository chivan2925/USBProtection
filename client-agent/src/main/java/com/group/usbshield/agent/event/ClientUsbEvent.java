package com.group.usbshield.agent.event;

import java.time.Instant;
import java.util.Map;

/**
 * Member 2 — Week 2: enriched domain event handed to Member 3's transport.
 *
 * <p>Field names follow the Week 1 Agent/Server JSON contract. Member 2
 * supplies truthful USBGuard decision and active session attribution;
 * Member 3 retains ownership of UsbEventPayload, EventSenderService and HTTP.
 * UsbEventEnricher and UsbGuardEventListener provide the M2-to-M3 handoff.</p>
 */
public record ClientUsbEvent(
        String endpointId,
        String hostname,
        String linuxUsername,
        Long linuxUid,
        String sessionId,
        String sessionType,
        String seat,
        Map<String, Object> device,
        String eventType,
        String decision,
        Instant occurredAt
) {
    public ClientUsbEvent {
        device = device == null ? Map.of() : Map.copyOf(device);
    }
}
