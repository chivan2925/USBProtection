package com.group.usbshield.agent.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.sync.dto.EnrollmentRequest;
import com.group.usbshield.agent.sync.dto.EnrollmentResponse;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EndpointManagerTest {

    @TempDir
    Path tempDir;

    private AgentProperties properties;
    private SystemInfoProvider systemInfoProvider;
    private AdminServerClient adminServerClient;
    private ObjectMapper objectMapper;
    private EndpointManager endpointManager;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setDataDir(tempDir.toString());
        properties.setEnrollmentToken("test-secret");

        systemInfoProvider = mock(SystemInfoProvider.class);
        when(systemInfoProvider.getHostname()).thenReturn("ubuntu-test-host");
        when(systemInfoProvider.getMachineId()).thenReturn("machine-123");
        when(systemInfoProvider.getIpAddress()).thenReturn("192.168.1.50");

        adminServerClient = mock(AdminServerClient.class);
        objectMapper = new ObjectMapper();

        endpointManager = new EndpointManager(properties, systemInfoProvider, adminServerClient, objectMapper);
        endpointManager.init();
    }

    @Test
    @DisplayName("ensureEnrolled performs enrollment, saves credentials, and returns true")
    void ensureEnrolledSuccess() {
        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-UUID-999")
                .agentToken("token-secret-xyz")
                .currentPolicyVersion(0L)
                .build();
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenReturn(Optional.of(response));

        boolean enrolled = endpointManager.ensureEnrolled();

        assertTrue(enrolled);
        assertEquals("EP-UUID-999", endpointManager.getEndpointId());
        assertNotNull(endpointManager.getCurrentRegistration());
        assertEquals("token-secret-xyz", endpointManager.getCurrentRegistration().getAgentToken());

        // File persistence check
        Path storageFile = tempDir.resolve("endpoint.json");
        assertTrue(Files.exists(storageFile), "endpoint.json must be persisted");
    }

    @Test
    @DisplayName("loadLocalRegistration reloads existing credentials across restarts")
    void loadExistingRegistration() throws Exception {
        EndpointRegistration saved = EndpointRegistration.builder()
                .endpointId("EP-EXISTING")
                .agentToken("tok-existing")
                .currentPolicyVersion(5L)
                .pendingAckVersion(5L)
                .build();

        Path storageFile = tempDir.resolve("endpoint.json");
        objectMapper.writeValue(storageFile.toFile(), saved);

        // Create new EndpointManager instance pointing to the same directory
        EndpointManager reloaded = new EndpointManager(properties, systemInfoProvider, adminServerClient, objectMapper);
        reloaded.init();

        assertTrue(reloaded.ensureEnrolled(), "Should be enrolled immediately from disk");
        assertEquals("EP-EXISTING", reloaded.getEndpointId());
        assertEquals(5L, reloaded.getCurrentRegistration().getCurrentPolicyVersion());
        assertEquals(5L, reloaded.getCurrentRegistration().getPendingAckVersion());
        verify(adminServerClient, never()).enroll(any());
    }

    @Test
    @DisplayName("updateAppliedPolicyVersion and clearPendingAck update registration correctly")
    void policyVersionAndPendingAckCycle() {
        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();
        when(adminServerClient.enroll(any())).thenReturn(Optional.of(response));
        endpointManager.ensureEnrolled();

        endpointManager.updateAppliedPolicyVersion(2L);
        assertEquals(2L, endpointManager.getCurrentRegistration().getCurrentPolicyVersion());
        assertEquals(2L, endpointManager.getCurrentRegistration().getPendingAckVersion());

        endpointManager.clearPendingAck();
        assertEquals(2L, endpointManager.getCurrentRegistration().getCurrentPolicyVersion());
        assertNull(endpointManager.getCurrentRegistration().getPendingAckVersion());
    }

    @Test
    @DisplayName("loadLocalRegistration with corrupted file handles error gracefully and allows fresh enrollment")
    void corruptedStorageFileReEnrolls() throws Exception {
        Path storageFile = tempDir.resolve("endpoint.json");
        Files.writeString(storageFile, "{ corrupted invalid json content !!!");

        EndpointManager reloaded = new EndpointManager(properties, systemInfoProvider, adminServerClient, objectMapper);
        reloaded.init();

        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-RECOVERED")
                .agentToken("token-recovered")
                .currentPolicyVersion(0L)
                .build();
        when(adminServerClient.enroll(any())).thenReturn(Optional.of(response));

        assertTrue(reloaded.ensureEnrolled(), "Should enroll afresh when storage file is corrupted");
        assertEquals("EP-RECOVERED", reloaded.getEndpointId());
    }

    @Test
    @DisplayName("ensureEnrolled returns false and aborts when credential cannot be persisted to disk")
    void ensureEnrolledFailsSafelyOnDiskWriteError() throws Exception {
        Path blockerFile = tempDir.resolve("blocker");
        Files.writeString(blockerFile, "I am a file, not a directory");

        AgentProperties brokenProps = new AgentProperties();
        brokenProps.setDataDir(blockerFile.resolve("subpath").toString());
        brokenProps.setEnrollmentToken("test-secret");

        EndpointManager failManager = new EndpointManager(brokenProps, systemInfoProvider, adminServerClient, objectMapper);
        failManager.init();

        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-UNSAVED")
                .agentToken("tok-unsaved")
                .currentPolicyVersion(0L)
                .build();
        when(adminServerClient.enroll(any())).thenReturn(Optional.of(response));

        boolean enrolled = failManager.ensureEnrolled();
        assertFalse(enrolled, "Enrollment must be marked failed if credentials cannot be written to disk");
        assertNull(failManager.getCurrentRegistration(), "Registration must not be retained in memory when disk save fails");
    }
}
