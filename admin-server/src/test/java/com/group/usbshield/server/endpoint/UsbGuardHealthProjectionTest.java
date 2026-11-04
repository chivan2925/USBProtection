package com.group.usbshield.server.endpoint;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UsbGuardHealthProjectionTest {
    private AdminEndpointController controller(EndpointRepository repo) {
        var ctrl = new AdminEndpointController(repo);
        ReflectionTestUtils.setField(ctrl,"offlineAfter",90L);
        return ctrl;
    }
    @Test void reportsHealthyOnlyForRecentOnlineAckedPolicy() {
        var repo=mock(EndpointRepository.class);
        var e=Endpoint.builder().endpointId("EP-01").hostname("lab").build();
        e.setPolicyVersion(2L);e.setAppliedPolicyVersion(2L);
        e.setLastSeen(Instant.now());e.setUsbGuardCheckedAt(Instant.now());e.setUsbGuardReportedStatus("HEALTHY");
        // F14 changed endpoint listing from Sort to Pageable; mock the actual overloads.
        when(repo.findAll(any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(e), call.getArgument(0), 1));
        when(repo.findAll(org.mockito.ArgumentMatchers.<Specification<Endpoint>>any(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(e), call.getArgument(1), 1));
        var api=controller(repo);
        // Primary paginated Admin API and bounded legacy endpoint list must agree.
        assertEquals("HEALTHY",api.page(0, 20, null, null).content().get(0).usbGuardStatus());
        assertEquals("HEALTHY",api.list().get(0).usbGuardStatus());
        e.setAppliedPolicyVersion(1L);
        assertEquals("DEGRADED",api.list().get(0).usbGuardStatus());
        e.setAppliedPolicyVersion(2L);e.setUsbGuardCheckedAt(Instant.now().minusSeconds(200));
        assertEquals("UNKNOWN",api.list().get(0).usbGuardStatus());
        assertTrue(api.list().get(0).usbGuardStale());
        e.setUsbGuardCheckedAt(Instant.now());e.setLastSeen(Instant.now().minusSeconds(200));
        assertEquals("UNKNOWN",api.list().get(0).usbGuardStatus());
    }
}
