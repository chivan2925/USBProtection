package com.group.usbshield.server.agentapi;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentTicketRepository extends JpaRepository<EnrollmentTicket, UUID> {
    interface TicketScope {
        long getWorkspaceId();
        String getEndpointId();
        String getSecretDigest();
    }

    Optional<TicketScope> findScopeById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EnrollmentTicket t where t.id = :id and t.workspaceId = :workspaceId and t.endpointId = :endpoint")
    Optional<EnrollmentTicket> lock(UUID id, long workspaceId, String endpoint);

    @Modifying
    @Query("update EnrollmentTicket t set t.revoked = true where t.workspaceId = :workspaceId and t.endpointId = :endpoint and t.revoked = false")
    void revokeAll(long workspaceId, String endpoint);
}
