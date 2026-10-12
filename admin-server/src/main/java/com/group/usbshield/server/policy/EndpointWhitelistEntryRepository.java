package com.group.usbshield.server.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EndpointWhitelistEntryRepository extends JpaRepository<EndpointWhitelistEntry, Long> {
    List<EndpointWhitelistEntry> findByEndpointIdAndEnabledTrue(String endpointId);
    List<EndpointWhitelistEntry> findByEndpointId(String endpointId);
    Optional<EndpointWhitelistEntry> findByEndpointIdAndFingerprintValue(String endpointId, String fingerprintValue);
}
