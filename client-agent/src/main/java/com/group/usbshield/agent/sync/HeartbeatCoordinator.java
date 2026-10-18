package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.sync.dto.HeartbeatRequest;
import com.group.usbshield.agent.sync.dto.HeartbeatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class HeartbeatCoordinator {

    private final EndpointManager endpointManager;
    private final AdminServerClient adminServerClient;
    private final PolicySyncService policySyncService;
    private final OutboxSpoolService outboxSpoolService;

    public HeartbeatCoordinator(EndpointManager endpointManager,
                                AdminServerClient adminServerClient,
                                PolicySyncService policySyncService,
                                OutboxSpoolService outboxSpoolService) {
        this.endpointManager = endpointManager;
        this.adminServerClient = adminServerClient;
        this.policySyncService = policySyncService;
        this.outboxSpoolService = outboxSpoolService;
    }

    @Scheduled(fixedDelayString = "${usbshield.agent.heartbeat-interval-ms:5000}")
    public void performHeartbeatCycle() {
        // 1. Đảm bảo Endpoint đã được đăng ký
        if (!endpointManager.ensureEnrolled()) {
            log.debug("Heartbeat skipped: endpoint enrollment is pending.");
            return;
        }

        EndpointRegistration registration = endpointManager.getCurrentRegistration();
        String endpointId = registration.getEndpointId();
        String agentToken = registration.getAgentToken();
        Long localVersion = registration.getCurrentPolicyVersion() != null ? registration.getCurrentPolicyVersion() : 0L;

        // 2. Gửi nhịp tim lên Admin Server
        HeartbeatRequest request = HeartbeatRequest.builder()
                .endpointId(endpointId)
                .currentPolicyVersion(localVersion)
                .build();

        Optional<HeartbeatResponse> responseOpt = adminServerClient.sendHeartbeat(request, agentToken);
        if (responseOpt.isEmpty()) {
            log.debug("Heartbeat response empty (Server unreachable).");
            return;
        }

        HeartbeatResponse response = responseOpt.get();
        log.debug("Heartbeat ACK from server: status={}, serverPolicyVersion={}",
                response.getStatus(), response.getServerPolicyVersion());

        // 3. Retry pending policy ACK nếu có version đã áp dụng thành công nhưng server chưa ACK
        retryPendingAckIfAny(registration, endpointId, agentToken);

        // 4. Kiểm tra xem Server có phiên bản Whitelist mới hơn không
        if (response.getServerPolicyVersion() != null && response.getServerPolicyVersion() > localVersion) {
            log.info("New policy version detected on server (local: {}, server: {}). Initiating sync...",
                    localVersion, response.getServerPolicyVersion());
            policySyncService.syncPolicy(registration);
        }

        // 5. Nếu Server đang ONLINE, gửi bù các sự kiện cũ trong hàng đợi spool ngoại tuyến
        outboxSpoolService.drainOutbox(endpointId, agentToken, adminServerClient);
    }

    private void retryPendingAckIfAny(EndpointRegistration registration, String endpointId, String agentToken) {
        Long pendingAck = registration.getPendingAckVersion();
        if (pendingAck != null) {
            log.info("Retrying pending policy ACK for version {}...", pendingAck);
            com.group.usbshield.agent.sync.dto.PolicyAckRequest retryAck = com.group.usbshield.agent.sync.dto.PolicyAckRequest.builder()
                    .appliedPolicyVersion(pendingAck)
                    .build();
            if (adminServerClient.acknowledgePolicy(endpointId, agentToken, retryAck)) {
                endpointManager.clearPendingAck();
                log.info("Pending policy ACK for version {} successfully confirmed by server.", pendingAck);
            } else {
                log.warn("Retry of pending policy ACK for version {} failed. Will retry on next heartbeat cycle.", pendingAck);
            }
        }
    }
}
