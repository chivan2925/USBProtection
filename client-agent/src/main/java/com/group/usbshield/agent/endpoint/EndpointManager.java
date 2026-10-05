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
        // Ưu tiên đường dẫn cấu hình chuẩn (vd /var/lib/usbshield-agent)
        Path configuredPath = Path.of(properties.getDataDir());
        try {
            if (!Files.exists(configuredPath)) {
                Files.createDirectories(configuredPath);
            }
            this.storageFilePath = configuredPath.resolve("endpoint.json");
            log.info("Endpoint credential storage configured at: {}", storageFilePath);
        } catch (Exception e) {
            // Fallback sang thư mục local ./data/ nếu chạy trên Windows hoặc không có quyền root
            Path fallbackPath = Path.of("./data");
            try {
                Files.createDirectories(fallbackPath);
                this.storageFilePath = fallbackPath.resolve("endpoint.json");
                log.info("Fallback credential storage configured at: {}", storageFilePath);
            } catch (Exception ex) {
                log.error("Could not initialize local data storage directory: {}", ex.getMessage());
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
            this.currentRegistration = EndpointRegistration.builder()
                    .endpointId(response.getEndpointId())
                    .agentToken(response.getAgentToken())
                    .currentPolicyVersion(response.getCurrentPolicyVersion() != null ? response.getCurrentPolicyVersion() : 0L)
                    .build();
            saveRegistration();
            log.info("Endpoint successfully enrolled! Assigned ID: {}", currentRegistration.getEndpointId());
            return true;
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

    public synchronized void updateAppliedPolicyVersion(long newVersion) {
        if (currentRegistration != null) {
            currentRegistration.setCurrentPolicyVersion(newVersion);
            saveRegistration();
            log.info("Updated local applied policy version to {}", newVersion);
        }
    }

    private void saveRegistration() {
        if (storageFilePath == null || currentRegistration == null) {
            return;
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFilePath.toFile(), currentRegistration);
        } catch (Exception e) {
            log.error("Failed to persist endpoint registration to {}: {}", storageFilePath, e.getMessage());
        }
    }
}
