package com.group.usbshield.server.policy;
import com.group.usbshield.server.endpoint.*;
import com.group.usbshield.server.device.*;
import com.group.usbshield.server.policy.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
@Service @RequiredArgsConstructor
public class EndpointPolicyService {
    private final EndpointRepository endpoints;
    private final EndpointWhitelistEntryRepository entries;
    private final UsbDeviceRepository devices;
    public record AllowRequest(Long usbDeviceId){}
    public record EntryResponse(Long id,String endpointId,Long usbDeviceId,String fingerprintType,String fingerprintValue,Boolean enabled){}
    private EntryResponse view(EndpointWhitelistEntry e){return new EntryResponse(e.getId(),e.getEndpointId(),e.getUsbDeviceId(),e.getFingerprintType(),e.getFingerprintValue(),e.getEnabled());}
    private Endpoint endpoint(String id){return endpoints.findByEndpointId(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Endpoint not found"));}
    private Endpoint lock(String id){return endpoints.lockByEndpointId(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Endpoint not found"));}
    @Transactional(readOnly=true) public List<EntryResponse> list(String id){endpoint(id);return entries.findByEndpointIdAndEnabledTrue(id).stream().map(this::view).toList();}
    @Transactional public EntryResponse allow(String id,AllowRequest request){
        var endpoint=lock(id);
        if(request.usbDeviceId()==null)throw new ResponseStatusException(BAD_REQUEST,"usbDeviceId required");
        var device=devices.findById(request.usbDeviceId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Device not found"));
        // Week 2 uses USBGuard HASH only; never broaden identity to a model/name.
        if(device.getDeviceHash()==null || device.getDeviceHash().isBlank())throw new ResponseStatusException(CONFLICT,"Device has no stable HASH; allow requires identity review");
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
        var rules=entries.findByEndpointIdAndEnabledTrue(id).stream().map(e->{
            var b=WhitelistedDeviceDto.builder().fingerprintType(e.getFingerprintType()).fingerprintValue(e.getFingerprintValue());
            if("HASH".equals(e.getFingerprintType()))b.deviceHash(e.getFingerprintValue());
            else throw new ResponseStatusException(CONFLICT,"Legacy whitelist requires HASH identity review");
            if(e.getUsbDeviceId()!=null)devices.findById(e.getUsbDeviceId()).ifPresent(d->b.vendorId(d.getVendorId()).productId(d.getProductId()).serialNumber(d.getSerialNumber()).deviceName(d.getDeviceName()));
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
