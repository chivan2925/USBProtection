package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.sync.dto.PolicyResponse;
import com.group.usbshield.agent.sync.dto.WhitelistedDeviceDto;
import com.group.usbshield.agent.usbguard.UsbGuardGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PolicySyncServiceTest {

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
    @DisplayName("Khi áp policy thành công -> Cập nhật version và gửi ACK lên Server")
    void testSyncPolicySuccess() {
        EndpointRegistration registration = EndpointRegistration.builder()
                .endpointId("EP-001")
                .agentToken("token-123")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of(
                        WhitelistedDeviceDto.builder()
                                .vendorId("0951")
                                .productId("1665")
                                .fingerprintValue("id 0951:1665")
                                .build()
                ))
                .build();

        when(adminServerClient.fetchPolicy("EP-001", "token-123")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(true);
        when(endpointManager.updateAppliedPolicyVersion(2L)).thenReturn(true);
        when(adminServerClient.acknowledgePolicy(eq("EP-001"), eq("token-123"), any())).thenReturn(true);
        when(endpointManager.clearPendingAck()).thenReturn(true);

        boolean result = policySyncService.syncPolicy(registration);

        assertTrue(result);
        verify(endpointManager, times(1)).updateAppliedPolicyVersion(2L);
        verify(adminServerClient, times(1)).acknowledgePolicy(eq("EP-001"), eq("token-123"), any());
        verify(endpointManager, times(1)).clearPendingAck();
    }

    @Test
    @DisplayName("Khi áp policy THẤT BẠI (lỗi quyền root/file) -> KHÔNG cập nhật version và KHÔNG gửi ACK")
    void testSyncPolicyFailureAbortsUpdateAndAck() {
        EndpointRegistration registration = EndpointRegistration.builder()
                .endpointId("EP-001")
                .agentToken("token-123")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of(
                        WhitelistedDeviceDto.builder()
                                .vendorId("0951")
                                .productId("1665")
                                .fingerprintValue("id 0951:1665")
                                .build()
                ))
                .build();

        when(adminServerClient.fetchPolicy("EP-001", "token-123")).thenReturn(Optional.of(response));
        // Giả lập apply thất bại (do lỗi quyền hạn hoặc ghi file)
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(false);

        boolean result = policySyncService.syncPolicy(registration);

        assertFalse(result, "Khi áp policy thất bại thì syncPolicy phải trả về false");
        verify(endpointManager, never()).updateAppliedPolicyVersion(anyLong());
        verify(adminServerClient, never()).acknowledgePolicy(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Khi USBGuard áp dụng thành công nhưng LƯU ĐĨA THẤT BẠI -> HỦY GỬI ACK để tránh false-ACK")
    void testSyncPolicyPersistenceFailureAbortsAck() {
        EndpointRegistration registration = EndpointRegistration.builder()
                .endpointId("EP-001")
                .agentToken("token-123")
                .currentPolicyVersion(1L)
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyVersion(2L)
                .whitelistedDevices(List.of(
                        WhitelistedDeviceDto.builder()
                                .vendorId("0951")
                                .productId("1665")
                                .fingerprintValue("id 0951:1665")
                                .build()
                ))
                .build();

        when(adminServerClient.fetchPolicy("EP-001", "token-123")).thenReturn(Optional.of(response));
        when(usbGuardGateway.applyWhitelist(anyList())).thenReturn(true);
        // Giả lập lưu đĩa thất bại (disk full, permission denied, filesystem error)
        when(endpointManager.updateAppliedPolicyVersion(2L)).thenReturn(false);

        boolean result = policySyncService.syncPolicy(registration);

        assertFalse(result, "Khi lưu version policy xuống đĩa thất bại thì syncPolicy phải trả về false để đảm bảo fail-safe");
        verify(endpointManager, times(1)).updateAppliedPolicyVersion(2L);
        // Bắt buộc TUYỆT ĐỐI KHÔNG được gửi ACK lên Server khi chưa lưu bền vững
        verify(adminServerClient, never()).acknowledgePolicy(anyString(), anyString(), any());
        verify(endpointManager, never()).clearPendingAck();
    }
}
