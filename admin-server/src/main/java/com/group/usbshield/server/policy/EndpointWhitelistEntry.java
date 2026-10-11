package com.group.usbshield.server.policy;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "endpoint_whitelist_entries", indexes = {
    @Index(name = "idx_endpoint_whitelist", columnList = "endpointId, enabled"),
    @Index(name = "idx_endpoint_fingerprint", columnList = "endpointId, fingerprintValue")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_endpoint_fingerprint", columnNames = {"endpointId", "fingerprintValue"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndpointWhitelistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String endpointId;

    private Long usbDeviceId;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String fingerprintType = "HASH";

    @Column(nullable = false, length = 255)
    private String fingerprintValue;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
        if (this.fingerprintType == null) {
            this.fingerprintType = "HASH";
        }
    }
}
