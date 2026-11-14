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
    @org.springframework.data.jpa.repository.Query("select e from Endpoint e where e.endpointId = :id")
    Optional<Endpoint> lockByEndpointId(@org.springframework.data.repository.query.Param("id") String id);
    /** Catalog-wide transaction lock; all Agent event writers use the same row. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Endpoint e where e.id = (select min(root.id) from Endpoint root)")
    Optional<Endpoint> lockDeviceCatalogCoordinator();
    Optional<Endpoint> findByMachineId(String machineId);
    List<Endpoint> findByEndpointIdIn(Collection<String> endpointIds);
    long countByLastSeenAfter(java.time.Instant cutoff);
    boolean existsByEndpointId(String endpointId);
}
