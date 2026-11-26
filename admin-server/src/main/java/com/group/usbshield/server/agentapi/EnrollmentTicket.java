package com.group.usbshield.server.agentapi;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollment_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentTicket {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private long workspaceId;
    @Column(nullable = false, length = 100, updatable = false)
    private String endpointId;
    @Column(nullable = false, length = 64, updatable = false)
    private String secretDigest;
    @Column(nullable = false, updatable = false)
    private Instant expiresAt;
    private boolean revoked;
    private UUID requestId;
    private Long claimGeneration;
    @Column(length = 64)
    private String claimCredentialDigest;
    private Instant receiptUntil;

    EnrollmentTicket(UUID id, long workspaceId, String endpointId, String secretDigest, Instant expiresAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.endpointId = endpointId;
        this.secretDigest = secretDigest;
        this.expiresAt = expiresAt;
    }

    void consume(UUID requestId, long generation, String credentialDigest, Instant until) {
        this.requestId = requestId;
        this.claimGeneration = generation;
        this.claimCredentialDigest = credentialDigest;
        this.receiptUntil = until;
    }
}
