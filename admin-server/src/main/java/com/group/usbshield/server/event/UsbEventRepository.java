package com.group.usbshield.server.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsbEventRepository extends JpaRepository<UsbEvent, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UsbEvent> {
    Optional<UsbEvent> findByWorkspaceIdAndEndpointIdAndEventId(long workspaceId, String endpointId, String eventId);
    Optional<UsbEvent> findByWorkspaceIdAndId(long workspaceId, Long id);
    boolean existsByWorkspaceIdAndEndpointIdAndUsbDeviceId(long workspaceId, String endpointId, Long usbDeviceId);
    long countByWorkspaceId(long workspaceId);
    List<UsbEvent> findByWorkspaceIdAndEndpointIdOrderByOccurredAtDesc(long workspaceId, String endpointId);
}
