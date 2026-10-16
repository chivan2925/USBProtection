package com.group.usbshield.server.endpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
@RestController @RequestMapping("/api/admin/endpoints") @RequiredArgsConstructor
public class AdminEndpointController {
    private final EndpointRepository endpoints;
    @Value("${usbshield.offline-after-seconds:90}") private long offlineAfter;
    public record EndpointResponse(String endpointId,String hostname,String ipAddress,String status,Instant lastSeen,Long policyVersion,Long appliedPolicyVersion){}
    private EndpointResponse view(Endpoint e){return new EndpointResponse(e.getEndpointId(),e.getHostname(),e.getIpAddress(),e.getLastSeen()!=null && e.getLastSeen().isAfter(Instant.now().minusSeconds(offlineAfter))?"ONLINE":"OFFLINE",e.getLastSeen(),e.getPolicyVersion(),e.getAppliedPolicyVersion());}
    @GetMapping public List<EndpointResponse> list(){return endpoints.findAll(org.springframework.data.domain.Sort.by("endpointId")).stream().map(this::view).toList();}
    @GetMapping("/{endpointId}") public EndpointResponse detail(@PathVariable String endpointId){return view(endpoints.findByEndpointId(endpointId).orElseThrow(()->new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Endpoint not found")));}
}
