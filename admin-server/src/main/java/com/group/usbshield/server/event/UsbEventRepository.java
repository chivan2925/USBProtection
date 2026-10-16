package com.group.usbshield.server.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsbEventRepository extends JpaRepository<UsbEvent, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UsbEvent> {
    List<UsbEvent> findByEndpointIdOrderByOccurredAtDesc(String endpointId);
    List<UsbEvent> findTop50ByOrderByOccurredAtDesc();
}
