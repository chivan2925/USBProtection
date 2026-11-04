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
    public record EndpointResponse(String endpointId,String hostname,String ipAddress,String operatingSystem,String currentUser,String status,Instant lastSeen,Long policyVersion,Long appliedPolicyVersion,
                                   String usbGuardStatus,Instant usbGuardCheckedAt,boolean usbGuardStale){}
    private EndpointResponse view(Endpoint e){
        Instant now = Instant.now();
        boolean online = e.getLastSeen() != null && e.getLastSeen().isAfter(now.minusSeconds(offlineAfter));
        // Never treat old/replayed/missing evidence or an ACK from a different
        // policy version as proof of CURRENT USBGuard enforcement.
        boolean stale = e.getUsbGuardCheckedAt() == null || !online ||
                e.getUsbGuardCheckedAt().isBefore(now.minusSeconds(offlineAfter));
        String observed = e.getUsbGuardReportedStatus();
        String effective = stale || observed == null ? "UNKNOWN" : observed;
        if ("HEALTHY".equals(effective) &&
                !java.util.Objects.equals(e.getPolicyVersion(), e.getAppliedPolicyVersion()))
            effective = "DEGRADED";
        return new EndpointResponse(e.getEndpointId(), e.getHostname(), e.getIpAddress(),
                e.getOperatingSystem(), e.getCurrentUser(), online ? "ONLINE" : "OFFLINE",
                e.getLastSeen(), e.getPolicyVersion(), e.getAppliedPolicyVersion(),
                effective, e.getUsbGuardCheckedAt(), stale);
    }
    @GetMapping public List<EndpointResponse> list(){return endpoints.findAll(org.springframework.data.domain.Sort.by("endpointId")).stream().map(this::view).toList();}
    @GetMapping("/{endpointId}") public EndpointResponse detail(@PathVariable String endpointId){return view(endpoints.findByEndpointId(endpointId).orElseThrow(()->new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Endpoint not found")));}
}
