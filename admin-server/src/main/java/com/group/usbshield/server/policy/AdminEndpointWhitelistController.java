package com.group.usbshield.server.policy;

import com.group.usbshield.server.auth.AdminIdentity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/admin/endpoints/{endpointId}/whitelist") @RequiredArgsConstructor
public class AdminEndpointWhitelistController {
    private final EndpointPolicyService service;
    @GetMapping public List<EndpointPolicyService.EntryResponse> list(@AuthenticationPrincipal AdminIdentity admin, @PathVariable String endpointId){return service.list(admin.workspaceId(), endpointId);}
    @PostMapping public EndpointPolicyService.EntryResponse allow(@AuthenticationPrincipal AdminIdentity admin, @PathVariable String endpointId,@RequestBody EndpointPolicyService.AllowRequest request){return service.allow(admin.workspaceId(), endpointId,request);}
    @DeleteMapping("/{entryId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AdminIdentity admin, @PathVariable String endpointId,@PathVariable Long entryId){service.revoke(admin.workspaceId(), endpointId,entryId);}
}
