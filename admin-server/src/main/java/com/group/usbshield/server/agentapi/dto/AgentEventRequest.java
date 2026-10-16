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
    @jakarta.validation.constraints.Size(max=100)
    private String endpointId;

    @jakarta.validation.constraints.Size(max=150)
    private String hostname;

    @Builder.Default
    private String linuxUsername = "UNKNOWN";

    private Long linuxUid;

    @jakarta.validation.constraints.Size(max=50)
    private String sessionId;

    @jakarta.validation.constraints.Size(max=50)
    private String sessionType;

    @jakarta.validation.constraints.Size(max=50)
    private String seat;

    @NotNull @jakarta.validation.Valid
    private AgentUsbDeviceDto device;

    @NotBlank(message = "eventType is required (e.g. CONNECTED, DISCONNECTED)")
    @jakarta.validation.constraints.Pattern(regexp="CONNECTED|DISCONNECTED")
    private String eventType;

    @NotBlank(message = "decision is required (e.g. ALLOWED, BLOCKED)")
    @jakarta.validation.constraints.Pattern(regexp="ALLOWED|BLOCKED")
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
