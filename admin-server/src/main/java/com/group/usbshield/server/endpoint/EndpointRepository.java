package com.group.usbshield.server.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
    Optional<Endpoint> findByEndpointId(String endpointId);
    Optional<Endpoint> findByMachineId(String machineId);
    boolean existsByEndpointId(String endpointId);
}
