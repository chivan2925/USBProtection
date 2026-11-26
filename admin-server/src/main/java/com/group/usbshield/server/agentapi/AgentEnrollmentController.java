package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEnrollmentRequest;
import com.group.usbshield.server.agentapi.dto.AgentEnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/enroll")
@RequiredArgsConstructor
public class AgentEnrollmentController {
    private final EnrollmentService enrollment;

    @PostMapping
    public ResponseEntity<AgentEnrollmentResponse> enroll(@Valid @RequestBody AgentEnrollmentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollment.claim(request));
    }
}
