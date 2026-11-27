package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEventRequest;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import com.group.usbshield.server.device.UsbCatalogWriter;
import com.group.usbshield.server.device.UsbDeviceRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.group.usbshield.server.event.UsbEvent;
import com.group.usbshield.server.event.UsbEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/agent/events")
@RequiredArgsConstructor
@Tag(name = "Agent Events", description = "Endpoints cho Client Agent đẩy sự kiện phát hiện và ngăn chặn USB")
public class AgentEventController {

    private final UsbEventRepository usbEventRepository;
    private final UsbDeviceRepository usbDeviceRepository;
    private final AgentCredentialService credentials;
    private final UsbCatalogWriter catalog;

    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    @Operation(summary = "Tiếp nhận USB Event từ Client Agent", description = "Lưu trữ sự kiện cắm/rút USB cùng thông tin Active User và quyết định ALLOWED/BLOCKED")
    public ResponseEntity<Map<String, Object>> handleUsbEvent(
            @Valid @RequestBody AgentEventRequest request,
            @AuthenticationPrincipal AgentIdentity agent) {

        String effectiveEndpointId = agent.endpointId();
        long workspaceId = agent.workspaceId();
        AgentCredentialService.bind(effectiveEndpointId, request.getEndpointId());
        // Reject implausible future events, but keep arbitrarily old offline events.
        if (request.getOccurredAt().isAfter(Instant.now().plus(java.time.Duration.ofMinutes(5)))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Event timestamp exceeds allowed future clock skew");
        }

        // Serialize dedup and recheck the authenticated generation with the endpoint locked.
        credentials.lockCurrent(agent);
        String payloadDigest = AgentEventCanonicalDigest.digest(effectiveEndpointId, request);
        if (request.getEventId() != null) {
            var existing = usbEventRepository.findByWorkspaceIdAndEndpointIdAndEventId(workspaceId, effectiveEndpointId, request.getEventId());
            if (existing.isPresent()) {
                UsbEvent stored = existing.get();
                boolean sameEvent = stored.getPayloadDigest() != null
                        ? stored.getPayloadDigest().equals(payloadDigest)
                        : legacyEventMatches(stored, request);
                if (!sameEvent) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT,
                            "Event ID was already used for a different event on this endpoint");
                }
                return ResponseEntity.ok(Map.of("success", true, "eventId", stored.getId(),
                        "receivedAt", stored.getReceivedAt()));
            }
        }

        AgentUsbDeviceDto deviceDto = request.getDevice();
        Long usbDeviceId = deviceDto == null ? null : catalog.observe(workspaceId, deviceDto);

        UsbEvent usbEvent = UsbEvent.builder()
                .workspaceId(workspaceId)
                .endpointId(effectiveEndpointId)
                .eventId(request.getEventId())
                .payloadDigest(payloadDigest)
                .hostname(request.getHostname())
                .usbDeviceId(usbDeviceId)
                .linuxUsername(request.getLinuxUsername())
                .linuxUid("UNKNOWN".equals(request.getLinuxUsername()) ? null : request.getLinuxUid())
                .sessionId("UNKNOWN".equals(request.getLinuxUsername()) ? null : request.getSessionId())
                .sessionType(request.getSessionType())
                .seat(request.getSeat())
                .eventType(request.getEventType())
                .decision(request.getDecision())
                .occurredAt(request.getOccurredAt())
                .receivedAt(Instant.now())
                .build();

        usbEvent = usbEventRepository.save(usbEvent);

        // Do not copy usernames, session identifiers, serials, hashes or raw event details into logs.
        log.info("Accepted USB event id={} endpointId={}", usbEvent.getId(), effectiveEndpointId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", usbEvent.getId(),
                "receivedAt", usbEvent.getReceivedAt()
        ));
    }
    /** Compatibility for pre-digest event rows created by older Agent binaries. */
    private boolean legacyEventMatches(UsbEvent stored, AgentEventRequest request) {
        String username = request.getLinuxUsername();
        boolean unknown = "UNKNOWN".equals(username);
        if (!Objects.equals(stored.getEventType(), request.getEventType())
                || !Objects.equals(stored.getDecision(), request.getDecision())
                || !Objects.equals(stored.getOccurredAt(), request.getOccurredAt())
                || !Objects.equals(stored.getHostname(), request.getHostname())
                || !Objects.equals(stored.getLinuxUsername(), username)
                || !Objects.equals(stored.getLinuxUid(), unknown ? null : request.getLinuxUid())
                || !Objects.equals(stored.getSessionId(), unknown ? null : request.getSessionId())
                || !Objects.equals(stored.getSessionType(), request.getSessionType())
                || !Objects.equals(stored.getSeat(), request.getSeat())) return false;
        AgentUsbDeviceDto requested = request.getDevice();
        if (requested == null) return stored.getUsbDeviceId() == null;
        if (stored.getUsbDeviceId() == null) return false;
        var device = usbDeviceRepository.findByWorkspaceIdAndId(stored.getWorkspaceId(), stored.getUsbDeviceId());
        return device.isPresent()
                && Objects.equals(device.get().getVendorId(), requested.getVendorId())
                && Objects.equals(device.get().getProductId(), requested.getProductId())
                && Objects.equals(device.get().getSerialNumber(), requested.getSerial())
                && Objects.equals(device.get().getDeviceName(), requested.getName())
                && Objects.equals(device.get().getDeviceHash(), requested.getHash())
                && Objects.equals(device.get().getInterfaces(), requested.getInterfaceClass());
    }

}
