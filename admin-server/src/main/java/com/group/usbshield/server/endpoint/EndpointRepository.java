package com.group.usbshield.server.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Endpoint> {
    Optional<Endpoint> findByEndpointId(String endpointId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Endpoint e where e.workspaceId = :workspaceId and e.endpointId = :id")
    Optional<Endpoint> lockByWorkspaceIdAndEndpointId(long workspaceId, String id);
    Optional<Endpoint> findByWorkspaceIdAndEndpointId(long workspaceId, String endpointId);
    List<Endpoint> findByWorkspaceIdAndEndpointIdIn(long workspaceId, Collection<String> endpointIds);
    org.springframework.data.domain.Page<Endpoint> findByWorkspaceId(long workspaceId, org.springframework.data.domain.Pageable page);
    long countByWorkspaceId(long workspaceId);
    long countByWorkspaceIdAndLastSeenAfter(long workspaceId, java.time.Instant cutoff);
}
