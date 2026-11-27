package com.group.usbshield.server.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "admin_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** Incremented on every credential change; invalidates all prior sessions. */
    @Column(name = "credential_version")
    @Builder.Default
    private Long credentialVersion = 0L;

    void advanceCredentialVersion() {
        credentialVersion = (credentialVersion == null ? 0L : credentialVersion) + 1;
    }

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
