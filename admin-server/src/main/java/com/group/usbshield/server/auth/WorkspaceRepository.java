package com.group.usbshield.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByOwnerAdminId(long ownerAdminId);
    boolean existsByIdAndOwnerAdminId(long id, long ownerAdminId);
}
