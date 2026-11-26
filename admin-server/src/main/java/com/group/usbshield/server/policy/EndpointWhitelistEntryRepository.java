package com.group.usbshield.server.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EndpointWhitelistEntryRepository extends JpaRepository<EndpointWhitelistEntry, Long> {
    List<EndpointWhitelistEntry> findByWorkspaceIdAndEndpointIdAndEnabledTrue(long workspaceId, String endpointId);
    Optional<EndpointWhitelistEntry> findByWorkspaceIdAndEndpointIdAndFingerprintValue(long workspaceId, String endpointId, String fingerprintValue);
    Optional<EndpointWhitelistEntry> findByWorkspaceIdAndEndpointIdAndId(long workspaceId, String endpointId, Long id);
}
