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

    @Column(length = 100, unique = true)
    private String machineId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String agentTokenHash;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 100)
    private String operatingSystem;

    // Avoid the PostgreSQL CURRENT_USER identifier; keep Java/JSON currentUser unchanged.
    @Column(name = "active_username", length = 100)
    private String currentUser;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "OFFLINE";

    private Instant lastSeen;

    /** Last Agent-reported daemon/policy evidence; a heartbeat is NOT itself health proof. */
    @Column(name = "usbguard_reported_status", length = 20)
    private String usbGuardReportedStatus;

    /** Timestamp assigned by Server upon receiving the health report. */
    @Column(name = "usbguard_checked_at")
    private Instant usbGuardCheckedAt;

    @Column(nullable = false)
    @Builder.Default
    private Long policyVersion = 1L;

    @Column(nullable = false)
    @Builder.Default
    private Long appliedPolicyVersion = 0L;

    @PrePersist
    public void prePersist() {
        if (this.policyVersion == null) {
            this.policyVersion = 1L;
        }
        if (this.appliedPolicyVersion == null) {
            this.appliedPolicyVersion = 0L;
        }
    }
}
