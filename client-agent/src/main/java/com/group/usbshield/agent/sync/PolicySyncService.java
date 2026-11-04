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
    private final UsbGuardHealthProbe healthProbe;

    @org.springframework.beans.factory.annotation.Autowired
    public PolicySyncService(AdminServerClient adminServerClient,
                             UsbGuardGateway usbGuardGateway,
                             EndpointManager endpointManager,
                             UsbGuardHealthProbe healthProbe) {
        this.adminServerClient = adminServerClient;
        this.usbGuardGateway = usbGuardGateway;
        this.endpointManager = endpointManager;
        this.healthProbe = healthProbe;
    }

    /** Backward-compatible constructor for isolated unit tests. */
    public PolicySyncService(AdminServerClient adminServerClient,
                             UsbGuardGateway usbGuardGateway,
                             EndpointManager endpointManager) {
        this(adminServerClient, usbGuardGateway, endpointManager, null);
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
        if (newVersion == null || newVersion < 0 || policyResponse.getWhitelistedDevices() == null) {
            log.error("Invalid server policy response; rejecting reconciliation");
            return false;
        }
        if (registration.getCurrentPolicyVersion() != null
                && newVersion < registration.getCurrentPolicyVersion()) {
            log.error("Server policy version regressed; refusing stale reconciliation");
            return false;
        }
        log.info("Applying new policy version {} with {} whitelisted devices",
                newVersion, policyResponse.getWhitelistedDevices().size());

        // Server contract is strictly HASH-based. Validate the ENTIRE payload before
        // invoking USBGuard; a skipped/invalid entry could otherwise be falsely ACKed.
        List<String> fingerprints = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (WhitelistedDeviceDto device : policyResponse.getWhitelistedDevices()) {
            if (device == null) {
                log.error("Null whitelist item; rejecting full policy and withholding ACK");
                return false;
            }
            String raw = device.getFingerprintValue();
            String mirrored = device.getDeviceHash();
            String hash = raw == null ? mirrored : raw;
            if (!validHash(hash) || (mirrored != null && !mirrored.equals(hash)) || !seen.add(hash)) {
                log.error("Invalid, conflicting or duplicate HASH in policy; rejecting full policy and withholding ACK");
                return false;
            }
            fingerprints.add(hash);
        }

        // Nạp vào USBGuard Engine (qua Adapter của Member 2)
        boolean applied;
        try {
            applied = usbGuardGateway.applyWhitelist(fingerprints);
        } catch (RuntimeException error) {
            log.error("USBGuard reconciliation failed; withholding policy ACK: {}", error.getMessage());
            return false;
        }
        if (!applied) {
            log.error("Failed to apply policy to USBGuard. Aborting version update and acknowledgment.");
            return false;
        }

        // Cập nhật version local và đánh dấu cần ACK (fail-safe persistence)
        boolean persisted = endpointManager.updateAppliedPolicyVersion(newVersion);
        if (!persisted) {
            log.error("Failed to persist applied policy version {} to disk. Aborting acknowledgment to maintain fail-safe state.", newVersion);
            return false;
        }

        // Keep the complete verified managed-rule set for live health inspection.
        if (healthProbe != null) healthProbe.recordAppliedHashes(fingerprints);

        // Gửi ACK lên Server
        PolicyAckRequest ackRequest = PolicyAckRequest.builder()
                .appliedPolicyVersion(newVersion)
                .ruleCount(fingerprints.size())
                .build();
        boolean ackSuccess = adminServerClient.acknowledgePolicy(endpointId, agentToken, ackRequest);
        if (ackSuccess) {
            boolean cleared = endpointManager.clearPendingAck();
            if (cleared) {
                log.info("Policy synchronization to version {} completed and ACK confirmed by server.", newVersion);
            } else {
                log.warn("Server confirmed ACK for version {}, but failed to clear pending ACK from disk. Will retry clearing on next cycle.", newVersion);
            }
        } else {
            log.warn("Policy version {} applied locally, but server ACK failed. Will retry on next heartbeat cycle.", newVersion);
        }

        return true;
    }
    /** Must remain identical to Server UsbHashIdentity / Agent UsbGuardRuleBuilder HASH guard. */
    static boolean validHash(String value) {
        return value != null && value.matches("[a-zA-Z0-9+/=_:.\\-]{1,255}")
                && value.matches(".*[a-zA-Z0-9].*")
                && !value.matches("[a-fA-F0-9]{4}:[a-fA-F0-9]{4}");
    }

}
