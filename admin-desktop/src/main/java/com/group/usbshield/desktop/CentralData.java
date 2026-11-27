package com.group.usbshield.desktop;

import java.util.List;

/** Representations of the Admin API. Policy enforcement stays on Central and Agent. */
final class CentralData {
    private CentralData() {}

    record Session(String username) {}
    record Page<T>(List<T> content, int page, int size, long totalElements) {
        Page {
            content = List.copyOf(content);
            if (page < 0 || size <= 0 || totalElements < 0) {
                throw new IllegalArgumentException("Invalid Central page");
            }
        }
        boolean hasNext() { return (page + 1L) * size < totalElements; }
    }
    record Summary(long totalDevices, long blockedDevices, long allowedDevices, long recentEvents, long blockedToday) {}
    record EndpointCounts(long onlineEndpoints, long offlineEndpoints) {}
    record Dashboard(Summary summary, EndpointCounts endpoints, List<Event> recent) {}
    record Endpoint(String endpointId, String hostname, String ipAddress, String operatingSystem,
                    String currentUser, String status, String lastSeen, Long policyVersion, Long appliedPolicyVersion,
                    String usbGuardStatus, String usbGuardCheckedAt, boolean usbGuardStale) {}
    record Device(Long usbDeviceId, String hash, String name, String endpointId, String endpointName,
                  String vendorId, String productId, String serialNumber, String status, String detectedAt) {}
    record Rule(Long id, String endpointId, String fingerprintValue, String name,
                String vendorId, String productId, String serial, String createdAt) {}
    record EventDevice(String name, String hash, String vendorId, String productId, String serial) {}
    record Event(Long id, String endpointId, String hostname, String linuxUsername, Long linuxUid,
                 String sessionId, String sessionType, String seat, EventDevice device, String eventType,
                 String decision, String occurredAt, String receivedAt) {}
    record Audit(String actorUsername, String action, String targetId, String occurredAt) {}
    record Slot(String endpointId) {}
    record Ticket(String expiresAt, String enrollmentToken) {
        @Override public String toString() { return "Ticket[expiresAt=" + expiresAt + "]"; }
    }
}
