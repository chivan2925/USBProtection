package com.group.usbshield.server.agentapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEventRequest {

    @NotBlank(message = "endpointId is required")
    private String endpointId;

    private String hostname;

    @Builder.Default
    private String linuxUsername = "UNKNOWN";

    private Long linuxUid;

    private String sessionId;

    private String sessionType;

    private String seat;

    private AgentUsbDeviceDto device;

    @NotBlank(message = "eventType is required (e.g. CONNECTED, DISCONNECTED)")
    private String eventType;

    @NotBlank(message = "decision is required (e.g. ALLOWED, BLOCKED)")
    private String decision;

    @NotNull(message = "occurredAt is required")
    private Instant occurredAt;

    public String getLinuxUsername() {
        if (linuxUsername == null || linuxUsername.isBlank()) {
            return "UNKNOWN";
        }
        return linuxUsername;
    }
}
