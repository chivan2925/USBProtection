package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.sync.dto.PolicyAckRequest;
import com.group.usbshield.agent.sync.dto.PolicyResponse;
import com.group.usbshield.agent.sync.dto.WhitelistedDeviceDto;
import com.group.usbshield.agent.usbguard.UsbGuardGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicyAckRecoveryTest {

    private AdminServerClient adminServerClient;
    private UsbGuardGateway usbGuardGateway;
    private EndpointManager endpointManager;
    private PolicySyncService policySyncService;

    @BeforeEach
    void setUp() {
        adminServerClient = mock(AdminServerClient.class);
        usbGuardGateway = mock(UsbGuardGateway.class);
        endpointManager = mock(EndpointManager.class);
        policySyncService = new PolicySyncService(adminServerClient, usbGuardGateway, endpointManager);
    }

    @Test
    @DisplayName("When USBGuard applies successfully but ACK HTTP fails, local version is updated and pending ACK retained")
    void applySuccessButAckFails() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of(
                        WhitelistedDeviceDto.builder()
                                .vendorId("0951")
                                .productId("1666")
                                .fingerprintValue("id 0951:1666")
                                .build()
                ))
                .build();

        when(adminServerClient.fetchPolicy("EP-01", "tok-01")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(true);
        when(endpointManager.updateAppliedPolicyVersion(2L)).thenReturn(true);
        // ACK network failure
        when(adminServerClient.acknowledgePolicy(eq("EP-01"), eq("tok-01"), any(PolicyAckRequest.class))).thenReturn(false);

        boolean synced = policySyncService.syncPolicy(reg);

        assertTrue(synced, "Sync should succeed locally if USBGuard accepted rules");
        verify(usbGuardGateway).applyWhitelist(anyList());
        verify(endpointManager).updateAppliedPolicyVersion(2L);
        // Should NOT clear pending ACK because server ACK returned false
        verify(endpointManager, never()).clearPendingAck();
    }

    @Test
    @DisplayName("When USBGuard apply fails, policy is NOT updated and no ACK is sent")
    void applyFailureAbortsAck() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of())
                .build();

        when(adminServerClient.fetchPolicy("EP-01", "tok-01")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(false);

        boolean synced = policySyncService.syncPolicy(reg);

        assertFalse(synced, "Sync should report failure if USBGuard fails to apply");
        verify(endpointManager, never()).updateAppliedPolicyVersion(anyLong());
        verify(adminServerClient, never()).acknowledgePolicy(any(), any(), any());
    }

    @Test
    @DisplayName("When USBGuard applies and ACK succeeds, pending ACK is cleared")
    void applyAndAckSuccess() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(3L)
                .whitelistedDevices(List.of())
                .build();

        when(adminServerClient.fetchPolicy("EP-01", "tok-01")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(true);
        when(endpointManager.updateAppliedPolicyVersion(3L)).thenReturn(true);
        when(endpointManager.clearPendingAck()).thenReturn(true);
        when(adminServerClient.acknowledgePolicy(eq("EP-01"), eq("tok-01"), any(PolicyAckRequest.class))).thenReturn(true);

        boolean synced = policySyncService.syncPolicy(reg);

        assertTrue(synced);
        verify(endpointManager).updateAppliedPolicyVersion(3L);
        verify(endpointManager).clearPendingAck();
    }

    @Test
    @DisplayName("When USBGuard applies successfully but disk persistence fails, ACK is aborted to prevent false-ACK")
    void applySuccessButPersistenceFailureAbortsAck() {
        EndpointRegistration reg = EndpointRegistration.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of())
                .build();

        when(adminServerClient.fetchPolicy("EP-01", "tok-01")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(true);
        when(endpointManager.updateAppliedPolicyVersion(2L)).thenReturn(false);

        boolean synced = policySyncService.syncPolicy(reg);

        assertFalse(synced, "Sync should fail when local persistence cannot be saved to disk");
        verify(endpointManager).updateAppliedPolicyVersion(2L);
        verify(adminServerClient, never()).acknowledgePolicy(any(), any(), any());
        verify(endpointManager, never()).clearPendingAck();
    }
}
