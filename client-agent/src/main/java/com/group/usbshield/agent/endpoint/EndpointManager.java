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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private record EnrollmentBinding(UUID installationId, String machineId) {}

    private record PendingEnrollment(String machineId, UUID installationId, UUID requestId,
                                     String ticketDigest, String agentToken) {
        @Override public String toString() { return "PendingEnrollment[requestId=" + requestId + "]"; }
    }

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
        if (properties.isRequireAbsoluteStorage() && !configuredPath.isAbsolute()) {
            this.storageFilePath = null;
            log.error("Live Agent dataDir must be absolute; refusing credential storage");
            return;
        }
        try {
            if (!Files.exists(configuredPath)) {
                Files.createDirectories(configuredPath);
            }
            this.storageFilePath = configuredPath.resolve("endpoint.json");
            log.info("Endpoint credential storage configured at: {}", storageFilePath);
        } catch (Exception e) {
            // Never relocate credentials under the process working directory.
            // No durable enrollment is attempted until configured storage is repaired.
            this.storageFilePath = null;
            log.error("Cannot initialize configured credential directory {}; enrollment disabled: {}",
                    configuredPath, e.getMessage());
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
            log.warn("Failed to read endpoint registration from disk");
        }
    }

    public synchronized boolean ensureEnrolled() {
        if (currentRegistration != null && currentRegistration.getEndpointId() != null) {
            return true;
        }

        UUID installationId = installationId();
        if (installationId == null || properties.getEnrollmentToken() == null
                || properties.getEnrollmentToken().isBlank()) return false;

        // The pending token is persisted BEFORE the HTTP request, so a lost
        // response, process crash or failed endpoint.json rename cannot orphan
        // an already committed endpoint on the server.
        String machineId = systemInfoProvider.getMachineId();
        PendingEnrollment pending = loadOrStageEnrollment(machineId, installationId);
        if (pending == null) {
            log.error("Cannot durably stage enrollment identity; refusing enrollment request");
            return false;
        }
        log.info("Attempting to enroll this endpoint with Admin Server...");
        EnrollmentRequest request = EnrollmentRequest.builder()
                .hostname(systemInfoProvider.getHostname())
                .machineId(pending.machineId())
                .ipAddress(systemInfoProvider.getIpAddressForServer(properties.getServerUrl()))
                .operatingSystem(systemInfoProvider.getOperatingSystem())
                .installationId(pending.installationId())
                .requestId(pending.requestId())
                .enrollmentToken(properties.getEnrollmentToken())
                .agentToken(pending.agentToken())
                .build();

        Optional<EnrollmentResponse> responseOpt = adminServerClient.enroll(request);
        if (responseOpt.isPresent()) {
            EnrollmentResponse response = responseOpt.get();
            if (response.getEndpointId() == null || response.getEndpointId().isBlank()
                    || !pending.agentToken().equals(response.getAgentToken())) {
                log.error("Enrollment server did not confirm staged Agent credential; refusing unsafe response");
                return false;
            }
            EndpointRegistration newRegistration = EndpointRegistration.builder()
                    .endpointId(response.getEndpointId())
                    .agentToken(response.getAgentToken())
                    .currentPolicyVersion(response.getCurrentPolicyVersion() != null ? response.getCurrentPolicyVersion() : 0L)
                    .build();

            boolean persisted = saveRegistration(newRegistration);
            if (persisted) {
                this.currentRegistration = newRegistration;
                try { Files.deleteIfExists(pendingFile()); }
                catch (Exception cleanup) { log.warn("Staged enrollment cleanup deferred: {}", cleanup.getMessage()); }
                log.info("Endpoint successfully enrolled! Assigned ID: {}", currentRegistration.getEndpointId());
                return true;
            } else {
                log.error("Failed to persist endpoint registration to disk at {}. Enrollment marked as failed to prevent non-durable state.", storageFilePath);
                return false;
            }
        } else {
            log.warn("Enrollment could not be completed; check Central availability and ticket status");
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
            long prevVersion = currentRegistration.getCurrentPolicyVersion();
            Long prevPending = currentRegistration.getPendingAckVersion();

            currentRegistration.setCurrentPolicyVersion(newVersion);
            currentRegistration.setPendingAckVersion(newVersion);
            boolean saved = saveRegistration();
            if (saved) {
                log.info("Updated local applied policy version to {} (marked as pending ACK)", newVersion);
                return true;
            } else {
                // Rollback in-memory state so memory does not diverge from disk state
                currentRegistration.setCurrentPolicyVersion(prevVersion);
                currentRegistration.setPendingAckVersion(prevPending);
                log.error("Failed to persist updated policy version {} to disk. Rolled back in-memory state to version {}.", newVersion, prevVersion);
                return false;
            }
        }
        return false;
    }

    public synchronized boolean clearPendingAck() {
        if (currentRegistration != null && currentRegistration.getPendingAckVersion() != null) {
            Long prevPending = currentRegistration.getPendingAckVersion();
            currentRegistration.setPendingAckVersion(null);
            boolean saved = saveRegistration();
            if (saved) {
                log.info("Cleared pending policy ACK");
                return true;
            } else {
                // Rollback in-memory state so memory retains pending ACK for retry
                currentRegistration.setPendingAckVersion(prevPending);
                log.error("Failed to persist cleared pending ACK to disk. Retained pending ACK version {} for retry.", prevPending);
                return false;
            }
        }
        return false;
    }

    public synchronized boolean saveRegistration() {
        return saveRegistration(this.currentRegistration);
    }

    private Path pendingFile() {
        return storageFilePath == null ? null : storageFilePath.resolveSibling("enrollment-pending.json");
    }

    private UUID installationId() {
        if (storageFilePath == null) return null;
        Path path = storageFilePath.resolveSibling("installation-id.json");
        try {
            if (Files.exists(path)) {
                UUID existing = objectMapper.readValue(path.toFile(), UUID.class);
                if (existing == null) log.error("Missing durable installation identity");
                return existing;
            }
            UUID created = UUID.randomUUID();
            return persistAtomically(path, created) ? created : null;
        } catch (IOException failure) {
            log.error("Cannot read durable installation identity");
            return null;
        }
    }

    private PendingEnrollment loadOrStageEnrollment(String machineId, UUID installationId) {
        Path staged = pendingFile();
        String ticketDigest = ticketDigest(properties.getEnrollmentToken());
        try {
            boolean stagedExists = !Files.notExists(staged);
            PendingEnrollment previous = !stagedExists ? null
                    : objectMapper.readValue(staged.toFile(), PendingEnrollment.class);
            if (stagedExists && (previous == null || !installationId.equals(previous.installationId())
                    || previous.machineId() == null || previous.machineId().isBlank()
                    || previous.requestId() == null || previous.agentToken() == null
                    || !previous.agentToken().matches("[0-9a-f]{64}"))) {
                log.error("Invalid staged enrollment identity; IT intervention required");
                return null;
            }
            EnrollmentBinding binding = loadOrBindMachineId(installationId, machineId, previous);
            if (binding == null) return null;
            if (previous != null && ticketDigest.equals(previous.ticketDigest())) return previous;

            byte[] randomBytes = new byte[32];
            TOKEN_RANDOM.nextBytes(randomBytes);
            PendingEnrollment candidate = new PendingEnrollment(binding.machineId(), installationId, UUID.randomUUID(),
                    ticketDigest, HexFormat.of().formatHex(randomBytes));
            return persistAtomically(staged, candidate) ? candidate : null;
        } catch (IOException failure) {
            log.error("Cannot read durable enrollment state; IT intervention required");
            return null;
        }
    }

    private EnrollmentBinding loadOrBindMachineId(UUID installationId, String observedMachineId,
                                                   PendingEnrollment previous) throws IOException {
        Path path = storageFilePath.resolveSibling("enrollment-binding.json");
        String installationFallback = "installation:" + installationId;
        boolean machineIdAvailable = observedMachineId != null && !observedMachineId.isBlank();
        boolean newBinding = Files.notExists(path);
        // An existing pending claim may already have committed even if Agent lacks its receipt.
        String selectedMachineId = previous != null ? previous.machineId()
                : machineIdAvailable ? observedMachineId : installationFallback;
        EnrollmentBinding binding = newBinding ? new EnrollmentBinding(installationId, selectedMachineId)
                : objectMapper.readValue(path.toFile(), EnrollmentBinding.class);
        if (binding == null || !installationId.equals(binding.installationId())
                || binding.machineId() == null || binding.machineId().isBlank() || binding.machineId().length() > 100
                || (previous != null && !binding.machineId().equals(previous.machineId()))
                || (machineIdAvailable && !installationFallback.equals(binding.machineId())
                    && !binding.machineId().equals(observedMachineId))) {
            log.error("Enrollment binding mismatch or corruption; IT intervention required");
            return null;
        }
        // Save independently of the pending proof and endpoint.json so same-slot recovery can reuse it.
        if (newBinding && !persistAtomically(path, binding)) return null;
        return binding;
    }

    private static String ticketDigest(String ticket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public synchronized boolean saveRegistration(EndpointRegistration registration) {
        if (storageFilePath == null || registration == null) {
            log.error("Cannot persist endpoint registration: storageFilePath or registration is null");
            return false;
        }
        return persistAtomically(storageFilePath, registration);
    }

    private boolean persistAtomically(Path destination, Object data) {
        if (destination == null) return false;
        try {
            if (destination.getParent() != null && !Files.exists(destination.getParent())) {
                Files.createDirectories(destination.getParent());
            }
            Path parent = destination.toAbsolutePath().getParent();
            Path temporary = parent.resolve(".endpoint-" + UUID.randomUUID() + ".tmp");
            try {
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
                try {
                    Files.createFile(temporary, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException unsupported) {
                    Files.createFile(temporary); // Windows ACLs are not POSIX
                }
                try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                    output.write(json);
                    output.getFD().sync();
                }
                try {
                    Files.move(temporary, destination,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    // A same-directory rename is still safer than writing credentials in place.
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                // Sync the parent directory on Linux to make the rename durable across power loss.
                try (var directory = java.nio.channels.FileChannel.open(parent, java.nio.file.StandardOpenOption.READ)) {
                    directory.force(true);
                } catch (java.io.IOException | UnsupportedOperationException ignored) {
                    // Some filesystems cannot fsync directories; file contents were fsynced.
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            log.debug("Durably persisted Agent state to {}", destination);
            return true;
        } catch (Exception e) {
            log.error("Failed to persist Agent state to {} ({})", destination, e.getClass().getSimpleName());
            return false;
        }
    }
}
