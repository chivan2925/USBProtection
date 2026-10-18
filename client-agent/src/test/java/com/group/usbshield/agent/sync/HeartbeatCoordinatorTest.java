package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.sync.dto.HeartbeatRequest;
import com.group.usbshield.agent.sync.dto.HeartbeatResponse;
import com.group.usbshield.agent.sync.dto.PolicyAckRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeartbeatCoordinatorTest {

    private EndpointManager endpointManager;
    private AdminServerClient adminServerClient;
    private PolicySyncService policySyncService;
    private OutboxSpoolService outboxSpoolService;
    private HeartbeatCoordinator coordinator;

    @BeforeEach
    void setUp() {
        endpointManager = mock(EndpointManager.class);
        adminServerClient = mock(AdminServerClient.class);
        policySyncService = mock(PolicySyncService.class);
        outboxSpoolService = mock(OutboxSpoolService.class);

        coordinator = new HeartbeatCoordinator(
                endpointManager,
                adminServerClient,
                policySyncService,
                outboxSpoolService
        );
    }

    @Test
    @DisplayName("performHeartbeatCycle should do nothing if endpoint cannot enroll")
    void skipIfEnrollmentPending() {
        when(endpointManager.ensureEnrolled()).thenReturn(false);

        coordinator.performHeartbeatCycle();

        verify(adminServerClient, never()).sendHeartbeat(any(), any());
        verify(policySyncService, never()).syncPolicy(any());
        verify(outboxSpoolService, never()).drainOutbox(any(), any(), any());
    }

    @Test
    @DisplayName("performHeartbeatCycle triggers policy sync when server policy version is newer")
    void triggerSyncOnNewerServerVersion() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();

        when(endpointManager.ensureEnrolled()).thenReturn(true);
        when(endpointManager.getCurrentRegistration()).thenReturn(reg);

        HeartbeatResponse hbResp = HeartbeatResponse.builder()
                .status("ONLINE")
                .serverPolicyVersion(2L)
                .build();
        when(adminServerClient.sendHeartbeat(any(HeartbeatRequest.class), eq("tok-01")))
                .thenReturn(Optional.of(hbResp));

        coordinator.performHeartbeatCycle();

        verify(policySyncService, times(1)).syncPolicy(reg);
        verify(outboxSpoolService, times(1)).drainOutbox("EP-01", "tok-01", adminServerClient);
    }

    @Test
    @DisplayName("performHeartbeatCycle does NOT trigger policy sync if server version is not newer")
    void noSyncWhenVersionIsEqualOrLower() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(3L)
                .build();

        when(endpointManager.ensureEnrolled()).thenReturn(true);
        when(endpointManager.getCurrentRegistration()).thenReturn(reg);

        HeartbeatResponse hbResp = HeartbeatResponse.builder()
                .status("ONLINE")
                .serverPolicyVersion(3L)
                .build();
        when(adminServerClient.sendHeartbeat(any(HeartbeatRequest.class), eq("tok-01")))
                .thenReturn(Optional.of(hbResp));

        coordinator.performHeartbeatCycle();

        verify(policySyncService, never()).syncPolicy(any());
        verify(outboxSpoolService, times(1)).drainOutbox("EP-01", "tok-01", adminServerClient);
    }

    @Test
    @DisplayName("performHeartbeatCycle retries pending ACK if previous ACK failed")
    void retryPendingAckDuringHeartbeat() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(4L)
                .pendingAckVersion(4L)
                .build();

        when(endpointManager.ensureEnrolled()).thenReturn(true);
        when(endpointManager.getCurrentRegistration()).thenReturn(reg);

        HeartbeatResponse hbResp = HeartbeatResponse.builder()
                .status("ONLINE")
                .serverPolicyVersion(4L)
                .build();
        when(adminServerClient.sendHeartbeat(any(), eq("tok-01")))
                .thenReturn(Optional.of(hbResp));
        when(adminServerClient.acknowledgePolicy(eq("EP-01"), eq("tok-01"), any(PolicyAckRequest.class)))
                .thenReturn(true);

        coordinator.performHeartbeatCycle();

        ArgumentCaptor<PolicyAckRequest> captor = ArgumentCaptor.forClass(PolicyAckRequest.class);
        verify(adminServerClient).acknowledgePolicy(eq("EP-01"), eq("tok-01"), captor.capture());
        assertEquals(4L, captor.getValue().getAppliedPolicyVersion());
        verify(endpointManager).clearPendingAck();
    }
}
