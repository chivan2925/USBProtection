package com.group.usbshield.server.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsbEventRepository extends JpaRepository<UsbEvent, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UsbEvent> {
    Optional<UsbEvent> findByEndpointIdAndEventId(String endpointId, String eventId);
    boolean existsByEndpointIdAndUsbDeviceId(String endpointId, Long usbDeviceId);
    List<UsbEvent> findByEndpointIdOrderByOccurredAtDesc(String endpointId);
    List<UsbEvent> findTop50ByOrderByOccurredAtDesc();
}
