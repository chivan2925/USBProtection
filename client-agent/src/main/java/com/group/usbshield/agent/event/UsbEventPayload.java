package com.group.usbshield.agent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsbEventPayload {
    private String endpointId;
    private String hostname;
    private String linuxUsername;
    private Long linuxUid;
    private String sessionId;
    private String sessionType;
    private String seat;
    private Map<String, Object> device;
    private String eventType; // CONNECTED, DISCONNECTED
    private String decision;  // ALLOWED, BLOCKED
    private Instant occurredAt;
}
