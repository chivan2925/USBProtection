package com.group.usbshield.agent.sync;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.session.ActiveUserInfo;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ActiveUserResolver activeUserResolver;
    private final SystemInfoProvider systemInfoProvider;
    private static final long SUCCESS_VERIFY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
    private static final long FAILURE_RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private long lastVerificationAttempt;
    private long lastVerificationSuccess;

    @Autowired
    public HeartbeatCoordinator(EndpointManager endpointManager,
                                AdminServerClient adminServerClient,
                                PolicySyncService policySyncService,
                                OutboxSpoolService outboxSpoolService,
                                ActiveUserResolver activeUserResolver,
                                SystemInfoProvider systemInfoProvider) {
        this.endpointManager = endpointManager;
        this.adminServerClient = adminServerClient;
        this.policySyncService = policySyncService;
        this.outboxSpoolService = outboxSpoolService;
        this.activeUserResolver = activeUserResolver;
        this.systemInfoProvider = systemInfoProvider;
    }

    // Preserve existing isolated unit tests and their constructor.
    public HeartbeatCoordinator(EndpointManager endpointManager,
                                AdminServerClient adminServerClient,
                                PolicySyncService policySyncService,
                                OutboxSpoolService outboxSpoolService) {
        this(endpointManager, adminServerClient, policySyncService, outboxSpoolService, null, null);
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
        String currentUser = null;
        if (activeUserResolver != null) {
            try {
                ActiveUserInfo user = activeUserResolver.resolveCurrentActiveUser();
                currentUser = user != null && "RESOLVED".equals(user.status())
                        ? user.username() : "UNKNOWN";
            } catch (RuntimeException error) {
                currentUser = "UNKNOWN";
            }
        }
        HeartbeatRequest request = HeartbeatRequest.builder()
                .endpointId(endpointId)
                .currentPolicyVersion(localVersion)
                .ipAddress(systemInfoProvider == null ? null :
                        systemInfoProvider.getIpAddressForServer(adminServerClient.getServerUrl()))
                .operatingSystem(systemInfoProvider == null ? null : systemInfoProvider.getOperatingSystem())
                .currentUser(currentUser)
                .build();

        Optional<HeartbeatResponse> responseOpt = adminServerClient.sendHeartbeat(request, agentToken);
        if (responseOpt.isEmpty()) {
            log.debug("Heartbeat response empty (Server unreachable).");
            return;
        }

        HeartbeatResponse response = responseOpt.get();
        log.debug("Heartbeat ACK from server: status={}, serverPolicyVersion={}",
                response.getStatus(), response.getServerPolicyVersion());

        // 3. Never retry an old ACK before verifying that the local daemon still
        // enforces the desired policy. A previous ACK is not live health evidence.
        // 4. A version is not evidence that local USBGuard rules still match.
        // Reconcile on first heartbeat and periodically even if version is unchanged.
        Long desiredVersion = response.getServerPolicyVersion();
        boolean verifiedEnforcement = false;
        if (desiredVersion == null || desiredVersion < localVersion) {
            log.error("Missing/stale server policy version; local enforcement not verified");
        } else {
            long now = System.nanoTime();
            boolean changed = desiredVersion > localVersion;
            long retryInterval = lastVerificationSuccess == 0 ? FAILURE_RETRY_NANOS : SUCCESS_VERIFY_NANOS;
            boolean due = lastVerificationAttempt == 0 || now - lastVerificationAttempt >= retryInterval;
            if (changed || due) {
                lastVerificationAttempt = now;
                if (policySyncService.syncPolicy(registration)) {
                    lastVerificationSuccess = now;
                    verifiedEnforcement = true;
                } else {
                    lastVerificationSuccess = 0;
                    log.error("USBGuard reconciliation failed; do not treat policy ACK as verified enforcement");
                }
            } else {
                verifiedEnforcement = lastVerificationSuccess != 0;
            }
        }
        if (verifiedEnforcement) retryPendingAckIfAny(registration, endpointId, agentToken);

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
                boolean cleared = endpointManager.clearPendingAck();
                if (cleared) {
                    log.info("Pending policy ACK for version {} successfully confirmed by server and cleared from disk.", pendingAck);
                } else {
                    log.warn("Server confirmed policy ACK for version {}, but clearing pending ACK from disk failed. Retained for next cycle.", pendingAck);
                }
            } else {
                log.warn("Retry of pending policy ACK for version {} failed. Will retry on next heartbeat cycle.", pendingAck);
            }
        }
    }
}
