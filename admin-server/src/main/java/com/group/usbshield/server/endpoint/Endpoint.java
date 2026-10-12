package com.group.usbshield.server.endpoint;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "endpoints", indexes = {
    @Index(name = "idx_endpoint_id", columnList = "endpointId", unique = true),
    @Index(name = "idx_machine_id", columnList = "machineId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String endpointId;

    @Column(nullable = false, length = 150)
    private String hostname;

    @Column(length = 100)
    private String machineId;

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ONLINE";

    private Instant lastSeen;

    @Column(nullable = false)
    @Builder.Default
    private Long policyVersion = 1L;

    @Column(nullable = false)
    @Builder.Default
    private Long appliedPolicyVersion = 0L;

    @PrePersist
    public void prePersist() {
        if (this.lastSeen == null) {
            this.lastSeen = Instant.now();
        }
        if (this.policyVersion == null) {
            this.policyVersion = 1L;
        }
        if (this.appliedPolicyVersion == null) {
            this.appliedPolicyVersion = 0L;
        }
    }
}
