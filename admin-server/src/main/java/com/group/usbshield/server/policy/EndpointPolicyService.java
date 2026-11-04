package com.group.usbshield.server.policy;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.device.*;
import com.group.usbshield.server.event.UsbEventRepository;
import com.group.usbshield.server.policy.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.Instant;
import static org.springframework.http.HttpStatus.*;
@Service @RequiredArgsConstructor
public class EndpointPolicyService {
    private final EndpointRepository endpoints;
    private final EndpointWhitelistEntryRepository entries;
    private final UsbDeviceRepository devices;
    private final UsbEventRepository events;
    public record AllowRequest(Long usbDeviceId){}
    public record EntryResponse(Long id,String endpointId,Long usbDeviceId,String fingerprintType,String fingerprintValue,Boolean enabled,String name,String vendorId,String productId,String serial,Instant createdAt){}
    private EntryResponse view(EndpointWhitelistEntry e, UsbDevice device){
        return new EntryResponse(e.getId(),e.getEndpointId(),e.getUsbDeviceId(),e.getFingerprintType(),e.getFingerprintValue(),e.getEnabled(),
                device == null ? null : device.getDeviceName(),device == null ? null : device.getVendorId(),
                device == null ? null : device.getProductId(),device == null ? null : device.getSerialNumber(),e.getCreatedAt());
    }
    private EntryResponse view(EndpointWhitelistEntry e){
        return view(e, e.getUsbDeviceId() == null ? null : devices.findById(e.getUsbDeviceId()).orElse(null));
    }
    private Map<Long, UsbDevice> loadDevices(List<EndpointWhitelistEntry> whitelist){
        var ids = whitelist.stream().map(EndpointWhitelistEntry::getUsbDeviceId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, UsbDevice> result = new HashMap<>();
        devices.findAllById(ids).forEach(d -> result.put(d.getId(), d));
        return result;
    }
    private Endpoint endpoint(String id){return endpoints.findByEndpointId(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Endpoint not found"));}
    private Endpoint lock(String id){return endpoints.lockByEndpointId(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Endpoint not found"));}
    @Transactional(readOnly=true) public List<EntryResponse> list(String id){
        endpoint(id);
        var whitelist = entries.findByEndpointIdAndEnabledTrue(id);
        var byId = loadDevices(whitelist);
        return whitelist.stream().map(e -> view(e, e.getUsbDeviceId() == null ? null : byId.get(e.getUsbDeviceId()))).toList();
    }
    @Transactional public EntryResponse allow(String id,AllowRequest request){
        var endpoint=lock(id);
        if(request.usbDeviceId()==null)throw new ResponseStatusException(BAD_REQUEST,"usbDeviceId required");
        var device=devices.findById(request.usbDeviceId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Device not found"));
        // Explicit contract: this endpoint must have actually reported this USB.
        // The catalog is global, but its IDs must never authorize an unseen endpoint.
        if (!events.existsByEndpointIdAndUsbDeviceId(id, device.getId())) {
            throw new ResponseStatusException(CONFLICT,
                    "USB has not been observed on this endpoint; pre-authorization is unsupported");
        }
        // Week 2 uses USBGuard HASH only; never broaden identity to a model/name.
        if (!UsbHashIdentity.valid(device.getDeviceHash()))
            throw new ResponseStatusException(CONFLICT,"Invalid USBGuard HASH identity; manual review required");
        var entry=entries.findByEndpointIdAndFingerprintValue(id,device.getDeviceHash()).orElse(null);
        if(entry!=null && Boolean.TRUE.equals(entry.getEnabled()))return view(entry);
        if(entry==null)entry=EndpointWhitelistEntry.builder().endpointId(id).usbDeviceId(device.getId()).fingerprintType("HASH").fingerprintValue(device.getDeviceHash()).build();
        entry.setEnabled(true);entries.save(entry);endpoint.setPolicyVersion(endpoint.getPolicyVersion()+1);
        return view(entry);
    }
    @Transactional public void revoke(String id,Long entryId){
        var endpoint=lock(id);
        var e=entries.findById(entryId).filter(x->x.getEndpointId().equals(id)).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Whitelist entry not found"));
        if(Boolean.TRUE.equals(e.getEnabled())){e.setEnabled(false);endpoint.setPolicyVersion(endpoint.getPolicyVersion()+1);}
    }
    @Transactional public AgentPolicyResponse policy(String id){
        var endpoint=lock(id);
        var whitelist = entries.findByEndpointIdAndEnabledTrue(id);
        var byId = loadDevices(whitelist);
        var rules=whitelist.stream().map(e->{
            var b=WhitelistedDeviceDto.builder().fingerprintType(e.getFingerprintType()).fingerprintValue(e.getFingerprintValue());
            if ("HASH".equals(e.getFingerprintType())) {
                if (!UsbHashIdentity.valid(e.getFingerprintValue()))
                    throw new ResponseStatusException(CONFLICT,"Stored whitelist has invalid USBGuard HASH identity");
                b.deviceHash(e.getFingerprintValue());
            }
            else throw new ResponseStatusException(CONFLICT,"Legacy whitelist requires HASH identity review");
            var device = e.getUsbDeviceId() == null ? null : byId.get(e.getUsbDeviceId());
            if (device != null) b.vendorId(device.getVendorId()).productId(device.getProductId())
                    .serialNumber(device.getSerialNumber()).deviceName(device.getDeviceName());
            return b.build();
        }).toList();
        return AgentPolicyResponse.builder().policyVersion(endpoint.getPolicyVersion()).whitelistedDevices(rules).build();
    }
    @Transactional public Map<String,Object> ack(String id,long version){
        var e=lock(id);
        if(version<0)throw new ResponseStatusException(BAD_REQUEST,"Version must be nonnegative");
        if(version>e.getPolicyVersion() || version<e.getAppliedPolicyVersion())throw new ResponseStatusException(CONFLICT,"Future or stale policy ACK");
        e.setAppliedPolicyVersion(version);
        return Map.of("acknowledged",true,"appliedPolicyVersion",version,"desiredPolicyVersion",e.getPolicyVersion());
    }
}
