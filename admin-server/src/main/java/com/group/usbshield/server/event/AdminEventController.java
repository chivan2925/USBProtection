package com.group.usbshield.server.event;
import com.group.usbshield.server.device.*;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
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
    private EventResponse view(UsbEvent e){
        var device=e.getUsbDeviceId()==null?null:devices.findById(e.getUsbDeviceId()).map(d->AgentUsbDeviceDto.builder().vendorId(d.getVendorId()).productId(d.getProductId()).serial(d.getSerialNumber()).name(d.getDeviceName()).hash(d.getDeviceHash()).interfaceClass(d.getInterfaces()).build()).orElse(null);
        String hostname=e.getHostname()!=null?e.getHostname():endpoints.findByEndpointId(e.getEndpointId()).map(Endpoint::getHostname).orElse(null);
        return new EventResponse(e.getId(),e.getEndpointId(),hostname,e.getLinuxUsername(),e.getLinuxUid(),e.getSessionId(),e.getSessionType(),e.getSeat(),e.getUsbDeviceId(),device,e.getEventType(),e.getDecision(),e.getOccurredAt(),e.getReceivedAt());
    }
    @GetMapping public EventPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String endpointId,@RequestParam(required=false) String linuxUsername,
        @RequestParam(required=false) String decision,@RequestParam(required=false) String eventType,
        @RequestParam(required=false) Long deviceId,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){
        if(page<0 || size<1 || size>100 || (from!=null && to!=null && from.isAfter(to)) || (decision!=null && !Set.of("ALLOWED","BLOCKED").contains(decision)) || (eventType!=null && !Set.of("CONNECTED","DISCONNECTED").contains(eventType)))throw new ResponseStatusException(BAD_REQUEST,"Invalid query range or enum");
        var result=events.findAll((root,query,cb)->{
            List<Predicate> p=new ArrayList<>();
            if(endpointId!=null)p.add(cb.equal(root.get("endpointId"),endpointId));
            if(linuxUsername!=null)p.add(cb.equal(root.get("linuxUsername"),linuxUsername));
            if(decision!=null)p.add(cb.equal(root.get("decision"),decision));
            if(eventType!=null)p.add(cb.equal(root.get("eventType"),eventType));
            if(deviceId!=null)p.add(cb.equal(root.get("usbDeviceId"),deviceId));
            if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),from));
            if(to!=null)p.add(cb.lessThanOrEqualTo(root.get("occurredAt"),to));
            return cb.and(p.toArray(Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurredAt","id")));
        return new EventPage(result.getContent().stream().map(this::view).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @GetMapping("/{id}") public EventResponse detail(@PathVariable Long id){return view(events.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Event not found")));}
}
