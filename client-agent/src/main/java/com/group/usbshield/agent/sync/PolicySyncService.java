package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.sync.dto.PolicyAckRequest;
import com.group.usbshield.agent.sync.dto.PolicyResponse;
import com.group.usbshield.agent.sync.dto.WhitelistedDeviceDto;
import com.group.usbshield.agent.usbguard.UsbGuardGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PolicySyncService {

    private final AdminServerClient adminServerClient;
    private final UsbGuardGateway usbGuardGateway;
    private final EndpointManager endpointManager;

    public PolicySyncService(AdminServerClient adminServerClient,
                             UsbGuardGateway usbGuardGateway,
                             EndpointManager endpointManager) {
        this.adminServerClient = adminServerClient;
        this.usbGuardGateway = usbGuardGateway;
        this.endpointManager = endpointManager;
    }

    /**
     * Kéo desired policy mới từ Admin Server và nạp vào USBGuard Engine
     */
    public boolean syncPolicy(EndpointRegistration registration) {
        String endpointId = registration.getEndpointId();
        String agentToken = registration.getAgentToken();

        Optional<PolicyResponse> policyOpt = adminServerClient.fetchPolicy(endpointId, agentToken);
        if (policyOpt.isEmpty()) {
            log.warn("Could not retrieve desired policy from Admin Server");
            return false;
        }

        PolicyResponse policyResponse = policyOpt.get();
        Long newVersion = policyResponse.getPolicyVersion();
        log.info("Applying new policy version {} with {} whitelisted devices",
                newVersion, policyResponse.getWhitelistedDevices().size());

        // Chuyển đổi danh sách whitelist sang dạng fingerprint cho USBGuard
        List<String> fingerprints = new ArrayList<>();
        for (WhitelistedDeviceDto device : policyResponse.getWhitelistedDevices()) {
            if (device.getFingerprintValue() != null && !device.getFingerprintValue().isBlank()) {
                fingerprints.add(device.getFingerprintValue());
            } else if (device.getDeviceHash() != null) {
                fingerprints.add("hash \"" + device.getDeviceHash() + "\"");
            } else if (device.getVendorId() != null && device.getProductId() != null) {
                String rule = String.format("id %s:%s", device.getVendorId(), device.getProductId());
                if (device.getSerialNumber() != null && !device.getSerialNumber().isBlank()) {
                    rule += " serial \"" + device.getSerialNumber() + "\"";
                }
                fingerprints.add(rule);
            }
        }

        // Nạp vào USBGuard Engine (qua Adapter của Member 2)
        usbGuardGateway.applyWhitelist(fingerprints);

        // Cập nhật version local
        endpointManager.updateAppliedPolicyVersion(newVersion);

        // Gửi ACK lên Server
        PolicyAckRequest ackRequest = PolicyAckRequest.builder()
                .appliedPolicyVersion(newVersion)
                .build();
        adminServerClient.acknowledgePolicy(endpointId, agentToken, ackRequest);

        log.info("Policy synchronization to version {} completed successfully.", newVersion);
        return true;
    }
}
