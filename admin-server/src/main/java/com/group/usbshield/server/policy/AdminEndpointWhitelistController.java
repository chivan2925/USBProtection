package com.group.usbshield.server.policy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/admin/endpoints/{endpointId}/whitelist") @RequiredArgsConstructor
public class AdminEndpointWhitelistController {
    private final EndpointPolicyService service;
    @GetMapping public List<EndpointPolicyService.EntryResponse> list(@PathVariable String endpointId){return service.list(endpointId);}
    @PostMapping public EndpointPolicyService.EntryResponse allow(@PathVariable String endpointId,@RequestBody EndpointPolicyService.AllowRequest request){return service.allow(endpointId,request);}
    @DeleteMapping("/{entryId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String endpointId,@PathVariable Long entryId){service.revoke(endpointId,entryId);}
}
