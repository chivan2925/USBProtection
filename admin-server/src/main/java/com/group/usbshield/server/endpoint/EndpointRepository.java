package com.group.usbshield.server.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
    Optional<Endpoint> findByEndpointId(String endpointId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Endpoint e where e.endpointId = :id")
    Optional<Endpoint> lockByEndpointId(@org.springframework.data.repository.query.Param("id") String id);
    Optional<Endpoint> findByMachineId(String machineId);
    boolean existsByEndpointId(String endpointId);
}
