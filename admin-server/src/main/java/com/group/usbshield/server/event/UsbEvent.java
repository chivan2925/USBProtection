package com.group.usbshield.server.event;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "usb_events", indexes = {
    @Index(name = "idx_event_endpoint_time", columnList = "endpointId, occurredAt"),
    @Index(name = "idx_event_decision", columnList = "decision"),
    @Index(name = "idx_event_user", columnList = "linuxUsername")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsbEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String endpointId;

    private Long usbDeviceId;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String linuxUsername = "UNKNOWN";

    private Long linuxUid;

    @Column(length = 50)
    private String sessionId;

    @Column(length = 50)
    private String sessionType;

    @Column(length = 50)
    private String seat;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String decision;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
        if (this.linuxUsername == null || this.linuxUsername.isBlank()) {
            this.linuxUsername = "UNKNOWN";
        }
    }
}
