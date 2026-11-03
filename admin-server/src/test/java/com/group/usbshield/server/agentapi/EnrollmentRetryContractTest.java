package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEnrollmentRequest;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnrollmentRetryContractTest {
    @Test void retryWithOriginalStagedSecretReturnsSameTokenButMachineIdOnlyCannot() {
        EndpointRepository endpoints = mock(EndpointRepository.class);
        var existing = new AtomicReference<Endpoint>();
        when(endpoints.findByMachineId(eq("MACHINE-A"))).thenAnswer(inv -> Optional.ofNullable(existing.get()));
        when(endpoints.save(any(Endpoint.class))).thenAnswer(inv -> {
            Endpoint saved = inv.getArgument(0);
            existing.set(saved);
            return saved;
        });
        var controller = new AgentEnrollmentController(endpoints, new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(controller, "bootstrap", "enrollment-bootstrap");
        AgentEnrollmentRequest req = AgentEnrollmentRequest.builder()
                .hostname("ubuntu-lab").machineId("MACHINE-A")
                .enrollmentToken("enrollment-bootstrap")
                .agentToken("1".repeat(64)).build();

        var first = controller.enroll(req);
        var retry = controller.enroll(req);
        assertEquals(first.getEndpointId(), retry.getEndpointId());
        assertEquals(req.getAgentToken(), retry.getAgentToken());
        verify(endpoints, times(1)).save(any(Endpoint.class));
        req.setAgentToken("2".repeat(64));
        assertThrows(ResponseStatusException.class, () -> controller.enroll(req));
        req.setAgentToken(null);
        assertThrows(ResponseStatusException.class, () -> controller.enroll(req));
    }
}
