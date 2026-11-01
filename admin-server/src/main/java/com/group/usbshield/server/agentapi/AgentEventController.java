package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEventRequest;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
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

@Slf4j
@RestController
@RequestMapping("/api/agent/events")
@RequiredArgsConstructor
@Tag(name = "Agent Events", description = "Endpoints cho Client Agent đẩy sự kiện phát hiện và ngăn chặn USB")
public class AgentEventController {

    private final UsbEventRepository usbEventRepository;
    private final UsbDeviceRepository usbDeviceRepository;

    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    @Operation(summary = "Tiếp nhận USB Event từ Client Agent", description = "Lưu trữ sự kiện cắm/rút USB cùng thông tin Active User và quyết định ALLOWED/BLOCKED")
    public ResponseEntity<Map<String, Object>> handleUsbEvent(
            @Valid @RequestBody AgentEventRequest request,
            java.security.Principal principal) {

        String effectiveEndpointId = principal.getName();
        AgentCredentialService.bind(effectiveEndpointId, request.getEndpointId());

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
}
