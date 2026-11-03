package com.group.usbshield.server.event;
import com.group.usbshield.server.device.*;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/admin/events") @RequiredArgsConstructor
public class AdminEventController {
    private final UsbEventRepository events;
    private final UsbDeviceRepository devices;
    private final EndpointRepository endpoints;
    public record EventResponse(Long id,String endpointId,String hostname,String linuxUsername,Long linuxUid,String sessionId,String sessionType,String seat,Long usbDeviceId,AgentUsbDeviceDto device,String eventType,String decision,Instant occurredAt,Instant receivedAt){}
    public record EventPage(List<EventResponse> content,int page,int size,long totalElements,int totalPages){}
    private static AgentUsbDeviceDto deviceDto(UsbDevice d) {
        return AgentUsbDeviceDto.builder().vendorId(d.getVendorId()).productId(d.getProductId())
            .serial(d.getSerialNumber()).name(d.getDeviceName()).hash(d.getDeviceHash())
            .interfaceClass(d.getInterfaces()).build();
    }

    private EventResponse view(UsbEvent e, Map<Long, UsbDevice> byDevice,
                               Map<String, String> fallbackHostnames) {
        UsbDevice d = e.getUsbDeviceId() == null ? null : byDevice.get(e.getUsbDeviceId());
        String hostname = e.getHostname() == null ? fallbackHostnames.get(e.getEndpointId()) : e.getHostname();
        return new EventResponse(e.getId(), e.getEndpointId(), hostname, e.getLinuxUsername(),
            e.getLinuxUid(), e.getSessionId(), e.getSessionType(), e.getSeat(),
            e.getUsbDeviceId(), d == null ? null : deviceDto(d), e.getEventType(),
            e.getDecision(), e.getOccurredAt(), e.getReceivedAt());
    }

    // Batch metadata on one page: O(1) extra queries, not one lookup per event.
    private List<EventResponse> views(List<UsbEvent> batch) {
        if (batch.isEmpty()) return List.of();
        Set<Long> deviceIds = new HashSet<>();
        Set<String> fallbackIds = new HashSet<>();
        for (UsbEvent e : batch) {
            if (e.getUsbDeviceId() != null) deviceIds.add(e.getUsbDeviceId());
            if (e.getHostname() == null) fallbackIds.add(e.getEndpointId());
        }
        Map<Long, UsbDevice> byDevice = new HashMap<>();
        if (!deviceIds.isEmpty()) devices.findAllById(deviceIds).forEach(d -> byDevice.put(d.getId(), d));
        Map<String, String> byHostname = new HashMap<>();
        if (!fallbackIds.isEmpty()) endpoints.findByEndpointIdIn(fallbackIds)
            .forEach(e -> byHostname.put(e.getEndpointId(), e.getHostname()));
        return batch.stream().map(e -> view(e, byDevice, byHostname)).toList();
    }

    @Transactional(readOnly = true)
    @GetMapping public EventPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String endpointId,@RequestParam(required=false) String linuxUsername,
        @RequestParam(required=false) String decision,@RequestParam(required=false) String eventType,
        @RequestParam(required=false) Long deviceId,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(required=false) String endpoint,@RequestParam(required=false) String device,
        @RequestParam(required=false) String username){
        if(page<0 || size<1 || size>100 || (from!=null && to!=null && from.isAfter(to)) || (decision!=null && !Set.of("ALLOWED","BLOCKED").contains(decision)) || (eventType!=null && !Set.of("CONNECTED","DISCONNECTED","POLICY_APPLIED").contains(eventType)))throw new ResponseStatusException(BAD_REQUEST,"Invalid query range or enum");
        var result=events.findAll((root,query,cb)->{
            List<Predicate> p=new ArrayList<>();
            if(endpointId!=null)p.add(cb.equal(root.get("endpointId"),endpointId));
            if(linuxUsername!=null)p.add(cb.equal(root.get("linuxUsername"),linuxUsername));
            if(username!=null && !username.isBlank())
                p.add(cb.like(cb.lower(root.get("linuxUsername")), containsPattern(username), '\\'));
            if(decision!=null)p.add(cb.equal(root.get("decision"),decision));
            if(eventType!=null)p.add(cb.equal(root.get("eventType"),eventType));
            if(deviceId!=null)p.add(cb.equal(root.get("usbDeviceId"),deviceId));
            if(endpoint!=null && !endpoint.isBlank()) {
                String pattern = containsPattern(endpoint);
                var registeredName = query.subquery(Integer.class);
                var registered = registeredName.from(Endpoint.class);
                registeredName.select(cb.literal(1)).where(
                    cb.equal(registered.get("endpointId"), root.get("endpointId")),
                    cb.like(cb.lower(registered.get("hostname")), pattern, '\\'));
                // Match the hostname displayed by view(): captured event name,
                // with registry fallback only for legacy rows missing that name.
                p.add(cb.or(
                    cb.like(cb.lower(root.get("endpointId")), pattern, '\\'),
                    cb.like(cb.lower(root.get("hostname")), pattern, '\\'),
                    cb.and(cb.isNull(root.get("hostname")), cb.exists(registeredName))));
            }
            if(device!=null && !device.isBlank()) {
                var matchingDevice = query.subquery(Integer.class);
                var usb = matchingDevice.from(UsbDevice.class);
                matchingDevice.select(cb.literal(1)).where(
                    cb.equal(usb.get("id"), root.get("usbDeviceId")),
                    cb.like(cb.lower(usb.get("deviceName")), containsPattern(device), '\\'));
                Long id = numericDeviceId(device);
                // Historical rows can retain an ID after device details disappear.
                p.add(id == null ? cb.exists(matchingDevice)
                    : cb.or(cb.exists(matchingDevice), cb.equal(root.get("usbDeviceId"), id)));
            }
            if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),from));
            if(to!=null)p.add(cb.lessThanOrEqualTo(root.get("occurredAt"),to));
            return cb.and(p.toArray(Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurredAt","id")));
        return new EventPage(views(result.getContent()),page,size,result.getTotalElements(),result.getTotalPages());
    }
    private static String containsPattern(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
    private static Long numericDeviceId(String value) {
        try {
            long id = Long.parseLong(value.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    @Transactional(readOnly = true)
    @GetMapping("/{id}") public EventResponse detail(@PathVariable Long id){
        return views(List.of(events.findById(id).orElseThrow(() ->
            new ResponseStatusException(NOT_FOUND, "Event not found")))).get(0);
    }
}
