package com.group.usbshield.agent.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.sync.dto.EnrollmentRequest;
import com.group.usbshield.agent.sync.dto.EnrollmentResponse;
import com.group.usbshield.agent.system.SystemInfoProvider;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@Component
public class EndpointManager {

    private final AgentProperties properties;
    private final SystemInfoProvider systemInfoProvider;
    private final AdminServerClient adminServerClient;
    private final ObjectMapper objectMapper;

    @Getter
    private EndpointRegistration currentRegistration;
    private Path storageFilePath;

    public EndpointManager(AgentProperties properties,
                           SystemInfoProvider systemInfoProvider,
                           AdminServerClient adminServerClient,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.systemInfoProvider = systemInfoProvider;
        this.adminServerClient = adminServerClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        resolveStoragePath();
        loadLocalRegistration();
    }

    private void resolveStoragePath() {
        if (properties.getDataDir() == null || properties.getDataDir().isBlank()) {
            this.storageFilePath = null;
            log.error("Data directory is not configured");
            return;
        }

        Path configuredPath = Path.of(properties.getDataDir());
        try {
            if (!Files.exists(configuredPath)) {
                Files.createDirectories(configuredPath);
            }
            this.storageFilePath = configuredPath.resolve("endpoint.json");
            log.info("Endpoint credential storage configured at: {}", storageFilePath);
        } catch (Exception e) {
            // Chỉ fallback sang ./data nếu dataDir là default Linux path (/var/lib/usbshield-agent)
            if ("/var/lib/usbshield-agent".equals(properties.getDataDir())) {
                Path fallbackPath = Path.of("./data");
                try {
                    Files.createDirectories(fallbackPath);
                    this.storageFilePath = fallbackPath.resolve("endpoint.json");
                    log.info("Fallback credential storage configured at: {}", storageFilePath);
                } catch (Exception ex) {
                    this.storageFilePath = null;
                    log.error("Could not initialize local data storage directory: {}", ex.getMessage());
                }
            } else {
                this.storageFilePath = configuredPath.resolve("endpoint.json");
                log.warn("Could not pre-create configured dataDir {}, will attempt creation during save: {}", configuredPath, e.getMessage());
            }
        }
    }

    public synchronized void loadLocalRegistration() {
        if (storageFilePath == null || !Files.exists(storageFilePath)) {
            log.info("No existing endpoint credentials found. Ready for enrollment.");
            return;
        }

        try {
            this.currentRegistration = objectMapper.readValue(storageFilePath.toFile(), EndpointRegistration.class);
            log.info("Loaded endpoint credentials: EndpointId={}, CurrentPolicyVersion={}",
                    currentRegistration.getEndpointId(), currentRegistration.getCurrentPolicyVersion());
        } catch (Exception e) {
            log.warn("Failed to read endpoint registration from file: {}", e.getMessage());
        }
    }

    public synchronized boolean ensureEnrolled() {
        if (currentRegistration != null && currentRegistration.getEndpointId() != null) {
            return true;
        }

        log.info("Attempting to enroll this endpoint with Admin Server...");
        EnrollmentRequest request = EnrollmentRequest.builder()
                .hostname(systemInfoProvider.getHostname())
                .machineId(systemInfoProvider.getMachineId())
                .ipAddress(systemInfoProvider.getIpAddress())
                .enrollmentToken(properties.getEnrollmentToken())
                .build();

        Optional<EnrollmentResponse> responseOpt = adminServerClient.enroll(request);
        if (responseOpt.isPresent()) {
            EnrollmentResponse response = responseOpt.get();
            EndpointRegistration newRegistration = EndpointRegistration.builder()
                    .endpointId(response.getEndpointId())
                    .agentToken(response.getAgentToken())
                    .currentPolicyVersion(response.getCurrentPolicyVersion() != null ? response.getCurrentPolicyVersion() : 0L)
                    .build();

            boolean persisted = saveRegistration(newRegistration);
            if (persisted) {
                this.currentRegistration = newRegistration;
                log.info("Endpoint successfully enrolled! Assigned ID: {}", currentRegistration.getEndpointId());
                return true;
            } else {
                log.error("Failed to persist endpoint registration to disk at {}. Enrollment marked as failed to prevent non-durable state.", storageFilePath);
                return false;
            }
        } else {
            log.warn("Enrollment could not be completed at this time (Admin Server unreachable).");
            return false;
        }
    }

    public String getEndpointId() {
        if (currentRegistration != null && currentRegistration.getEndpointId() != null) {
            return currentRegistration.getEndpointId();
        }
        return "POC-UNENROLLED-" + systemInfoProvider.getHostname();
    }

    public synchronized boolean updateAppliedPolicyVersion(long newVersion) {
        if (currentRegistration != null) {
            currentRegistration.setCurrentPolicyVersion(newVersion);
            currentRegistration.setPendingAckVersion(newVersion);
            boolean saved = saveRegistration();
            if (saved) {
                log.info("Updated local applied policy version to {} (marked as pending ACK)", newVersion);
                return true;
            } else {
                log.error("Failed to persist updated policy version {} to disk", newVersion);
                return false;
            }
        }
        return false;
    }

    public synchronized boolean clearPendingAck() {
        if (currentRegistration != null && currentRegistration.getPendingAckVersion() != null) {
            currentRegistration.setPendingAckVersion(null);
            boolean saved = saveRegistration();
            if (saved) {
                log.info("Cleared pending policy ACK");
                return true;
            } else {
                log.error("Failed to persist cleared pending ACK to disk");
                return false;
            }
        }
        return false;
    }

    public synchronized boolean saveRegistration() {
        return saveRegistration(this.currentRegistration);
    }

    public synchronized boolean saveRegistration(EndpointRegistration registration) {
        if (storageFilePath == null || registration == null) {
            log.error("Cannot persist endpoint registration: storageFilePath or registration is null");
            return false;
        }
        try {
            if (storageFilePath.getParent() != null && !Files.exists(storageFilePath.getParent())) {
                Files.createDirectories(storageFilePath.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFilePath.toFile(), registration);
            log.debug("Successfully persisted endpoint registration to {}", storageFilePath);
            return true;
        } catch (Exception e) {
            log.error("Failed to persist endpoint registration to {}: {}", storageFilePath, e.getMessage());
            return false;
        }
    }
}
