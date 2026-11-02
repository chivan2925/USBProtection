package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEventRequest;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.EndpointRepository;
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
    private final EndpointRepository endpointRepository;

    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    @Operation(summary = "Tiếp nhận USB Event từ Client Agent", description = "Lưu trữ sự kiện cắm/rút USB cùng thông tin Active User và quyết định ALLOWED/BLOCKED")
    public ResponseEntity<Map<String, Object>> handleUsbEvent(
            @Valid @RequestBody AgentEventRequest request,
            java.security.Principal principal) {

        String effectiveEndpointId = principal.getName();
        AgentCredentialService.bind(effectiveEndpointId, request.getEndpointId());

        // Lock the authenticated endpoint row BEFORE the duplicate lookup and
        // insert. This serializes concurrent deliveries across server instances
        // using the database lock, without a check-then-insert uniqueness race.
        endpointRepository.lockByEndpointId(effectiveEndpointId).orElseThrow(
                () -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unknown endpoint"));
        String payloadDigest = AgentEventCanonicalDigest.digest(effectiveEndpointId, request);
        if (request.getEventId() != null) {
            var existing = usbEventRepository.findByEndpointIdAndEventId(effectiveEndpointId, request.getEventId());
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

        Long usbDeviceId = null;
        AgentUsbDeviceDto deviceDto = request.getDevice();
        if (deviceDto != null) {
            UsbDevice savedDevice = null;
            if (deviceDto.getHash() != null && !deviceDto.getHash().isBlank()) {
                savedDevice = usbDeviceRepository.findByDeviceHash(deviceDto.getHash()).orElse(null);
            }
            if (savedDevice == null) {
                savedDevice = UsbDevice.builder()
                        .vendorId(deviceDto.getVendorId() != null ? deviceDto.getVendorId() : "UNKNOWN")
                        .productId(deviceDto.getProductId() != null ? deviceDto.getProductId() : "UNKNOWN")
                        .serialNumber(deviceDto.getSerial())
                        .deviceHash(deviceDto.getHash())
                        .deviceName(deviceDto.getName())
                        .interfaces(deviceDto.getInterfaceClass())
                        .build();
                savedDevice = usbDeviceRepository.save(savedDevice);
            }
            usbDeviceId = savedDevice.getId();
        }

        UsbEvent usbEvent = UsbEvent.builder()
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
        var device = usbDeviceRepository.findById(stored.getUsbDeviceId());
        return device.isPresent()
                && Objects.equals(device.get().getVendorId(), requested.getVendorId())
                && Objects.equals(device.get().getProductId(), requested.getProductId())
                && Objects.equals(device.get().getSerialNumber(), requested.getSerial())
                && Objects.equals(device.get().getDeviceName(), requested.getName())
                && Objects.equals(device.get().getDeviceHash(), requested.getHash())
                && Objects.equals(device.get().getInterfaces(), requested.getInterfaceClass());
    }

}
