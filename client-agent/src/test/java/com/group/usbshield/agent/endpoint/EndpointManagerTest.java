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
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });

        boolean enrolled = endpointManager.ensureEnrolled();

        assertTrue(enrolled);
        assertEquals("EP-UUID-999", endpointManager.getEndpointId());
        assertNotNull(endpointManager.getCurrentRegistration());
        assertTrue(endpointManager.getCurrentRegistration().getAgentToken().matches("[0-9a-f]{64}"));

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
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });
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
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });

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
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });

        boolean enrolled = failManager.ensureEnrolled();
        assertFalse(enrolled, "Enrollment must be marked failed if credentials cannot be written to disk");
        assertNull(failManager.getCurrentRegistration(), "Registration must not be retained in memory when disk save fails");
    }

    @Test
    @DisplayName("updateAppliedPolicyVersion rolls back in-memory version on disk persistence failure")
    void updateAppliedPolicyVersionRollsBackOnDiskWriteFailure() throws Exception {
        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });
        endpointManager.ensureEnrolled();

        // Simulate disk write failure by replacing endpoint.json file with a directory
        Path storageFile = tempDir.resolve("endpoint.json");
        Files.deleteIfExists(storageFile);
        Files.createDirectory(storageFile);

        boolean updated = endpointManager.updateAppliedPolicyVersion(2L);
        assertFalse(updated, "Must report failure when persistence to disk fails");
        // Verify in-memory state was rolled back to previous valid values
        assertEquals(1L, endpointManager.getCurrentRegistration().getCurrentPolicyVersion(),
                "In-memory policy version must rollback to previous version on disk failure");
        assertNull(endpointManager.getCurrentRegistration().getPendingAckVersion(),
                "In-memory pending ACK must rollback to previous state on disk failure");
    }

    @Test
    @DisplayName("clearPendingAck rolls back and retains pending ACK in memory when disk persistence fails")
    void clearPendingAckRollsBackOnDiskWriteFailure() throws Exception {
        EnrollmentResponse response = EnrollmentResponse.builder()
                .endpointId("EP-01")
                .agentToken("tok-01")
                .currentPolicyVersion(1L)
                .build();
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(invocation -> {
            EnrollmentRequest request = invocation.getArgument(0);
            response.setAgentToken(request.getAgentToken());
            return Optional.of(response);
        });
        endpointManager.ensureEnrolled();

        // Successfully update version to 2 (pendingAck = 2)
        assertTrue(endpointManager.updateAppliedPolicyVersion(2L));
        assertEquals(2L, endpointManager.getCurrentRegistration().getPendingAckVersion());

        // Now simulate disk write failure by replacing endpoint.json with a directory
        Path storageFile = tempDir.resolve("endpoint.json");
        Files.deleteIfExists(storageFile);
        Files.createDirectory(storageFile);

        boolean cleared = endpointManager.clearPendingAck();
        assertFalse(cleared, "clearPendingAck must return false when disk persistence fails");
        // In-memory pendingAckVersion must NOT be lost, must retain 2L for retry
        assertEquals(2L, endpointManager.getCurrentRegistration().getPendingAckVersion(),
                "Pending ACK version must be retained in memory when disk write fails");
    }
    @Test
    void lostHttpResponseRetainsStagedCredentialAcrossAgentRestart() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(call -> {
            EnrollmentRequest request = call.getArgument(0);
            if (attempts.incrementAndGet() == 1) return Optional.empty();
            return Optional.of(EnrollmentResponse.builder().endpointId("EP-STAGED")
                    .agentToken(request.getAgentToken()).currentPolicyVersion(1L).build());
        });

        assertFalse(endpointManager.ensureEnrolled());
        assertTrue(Files.exists(tempDir.resolve("enrollment-pending.json")));
        EndpointManager restarted = new EndpointManager(properties, systemInfoProvider, adminServerClient, objectMapper);
        restarted.init();
        assertTrue(restarted.ensureEnrolled());
        assertEquals("EP-STAGED", restarted.getEndpointId());
        assertFalse(Files.exists(tempDir.resolve("enrollment-pending.json")));
        var sent = org.mockito.ArgumentCaptor.forClass(EnrollmentRequest.class);
        verify(adminServerClient, times(2)).enroll(sent.capture());
        assertEquals(sent.getAllValues().get(0).getAgentToken(), sent.getAllValues().get(1).getAgentToken());
        assertTrue(sent.getAllValues().get(0).getAgentToken().matches("[0-9a-f]{64}"));
    }

    @Test
    void committedEnrollmentCanRecoverAfterEndpointJsonSaveFailure() throws Exception {
        Files.createDirectory(tempDir.resolve("endpoint.json")); // fail credential rename AFTER HTTP success
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenAnswer(call -> {
            EnrollmentRequest request = call.getArgument(0);
            return Optional.of(EnrollmentResponse.builder().endpointId("EP-PERSIST-RETRY")
                    .agentToken(request.getAgentToken()).currentPolicyVersion(1L).build());
        });
        assertFalse(endpointManager.ensureEnrolled());
        assertNull(endpointManager.getCurrentRegistration());
        assertTrue(Files.exists(tempDir.resolve("enrollment-pending.json")));

        Files.delete(tempDir.resolve("endpoint.json"));
        EndpointManager restarted = new EndpointManager(properties, systemInfoProvider, adminServerClient, objectMapper);
        restarted.init();
        assertTrue(restarted.ensureEnrolled());
        assertEquals("EP-PERSIST-RETRY", restarted.getEndpointId());
        var sent = org.mockito.ArgumentCaptor.forClass(EnrollmentRequest.class);
        verify(adminServerClient, times(2)).enroll(sent.capture());
        assertEquals(sent.getAllValues().get(0).getAgentToken(), sent.getAllValues().get(1).getAgentToken());
    }

    @Test
    void stagedEnrollmentWithChangedMachineIdRefusesUntrustedIdentityReuse() {
        when(adminServerClient.enroll(any(EnrollmentRequest.class))).thenReturn(Optional.empty());
        assertFalse(endpointManager.ensureEnrolled());
        reset(adminServerClient);
        when(systemInfoProvider.getMachineId()).thenReturn("different-machine");
        assertFalse(endpointManager.ensureEnrolled());
        verifyNoInteractions(adminServerClient);
    }

}
