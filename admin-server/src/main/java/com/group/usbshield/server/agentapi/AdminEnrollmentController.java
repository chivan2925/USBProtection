package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.auth.AdminIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/enrollment")
@RequiredArgsConstructor
public class AdminEnrollmentController {
    private final EnrollmentService enrollment;

    public record SlotRequest(@NotNull UUID installationId, @NotBlank @Size(max = 150) String hostname) {}

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentService.Slot create(@AuthenticationPrincipal AdminIdentity admin, @Valid @RequestBody SlotRequest request) {
        return enrollment.createSlot(admin, request.installationId(), request.hostname());
    }

    @PostMapping("/{endpointId}/ticket")
    @ResponseStatus(HttpStatus.CREATED)
    public org.springframework.http.ResponseEntity<EnrollmentService.IssuedTicket> issue(
            @AuthenticationPrincipal AdminIdentity admin, @PathVariable String endpointId) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(enrollment.issue(admin, endpointId));
    }

    @DeleteMapping("/{endpointId}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AdminIdentity admin, @PathVariable String endpointId) {
        enrollment.revoke(admin, endpointId);
    }
}
