package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxSpoolServiceTest {

    @TempDir
    Path tempDir;

    private OutboxSpoolService outboxSpoolService;
    private ObjectMapper objectMapper;
    private AdminServerClient adminServerClient;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setDataDir(tempDir.toString());

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        adminServerClient = mock(AdminServerClient.class);

        outboxSpoolService = new OutboxSpoolService(properties, objectMapper);
        outboxSpoolService.init();
    }

    @Test
    @DisplayName("spool should persist event payload to disk")
    void spoolPersistsFile() throws Exception {
        UsbEventPayload payload = UsbEventPayload.builder()
                .endpointId("EP-01")
                .hostname("ubuntu-test")
                .linuxUsername("student01")
                .eventType("CONNECTED")
                .decision("BLOCKED")
                .occurredAt(Instant.now())
                .device(Map.of("vendorId", "0951", "productId", "1666"))
                .build();

        outboxSpoolService.spool(payload);

        Path spoolDir = tempDir.resolve("spool");
        assertTrue(Files.exists(spoolDir));

        try (var stream = Files.list(spoolDir)) {
            List<Path> files = stream.toList();
            assertEquals(1, files.size());
            assertTrue(files.get(0).getFileName().toString().startsWith("event_"));
        }
    }

    @Test
    @DisplayName("drainOutbox should deliver spooled events in FIFO order and delete delivered files")
    void drainOutboxDeliversAndDeletes() {
        UsbEventPayload p1 = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("user1")
                .occurredAt(Instant.now())
                .build();

        UsbEventPayload p2 = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("user2")
                .occurredAt(Instant.now())
                .build();

        outboxSpoolService.spool(p1);
        try { Thread.sleep(30); } catch (InterruptedException ignored) {}
        outboxSpoolService.spool(p2);

        when(adminServerClient.sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class)))
                .thenReturn(true);

        outboxSpoolService.drainOutbox("EP-01", "token-1", adminServerClient);

        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(adminServerClient, times(2)).sendEvent(eq("EP-01"), eq("token-1"), captor.capture());

        // Assert strictly FIFO order
        List<UsbEventPayload> delivered = captor.getAllValues();
        assertEquals("user1", delivered.get(0).getLinuxUsername(), "First spooled event must be delivered first (FIFO)");
        assertEquals("user2", delivered.get(1).getLinuxUsername(), "Second spooled event must be delivered second (FIFO)");

        Path spoolDir = tempDir.resolve("spool");
        try (var stream = Files.list(spoolDir)) {
            assertEquals(0, stream.count(), "All spooled files should have been removed after successful delivery");
        } catch (Exception e) {
            fail("Exception while reading spool dir: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("drainOutbox pauses on network false return without dropping files")
    void drainOutboxPausesOnNetworkError() {
        UsbEventPayload p1 = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("user1")
                .occurredAt(Instant.now())
                .build();

        outboxSpoolService.spool(p1);

        when(adminServerClient.sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class)))
                .thenReturn(false);

        outboxSpoolService.drainOutbox("EP-01", "token-1", adminServerClient);

        verify(adminServerClient, times(1)).sendEvent(any(), any(), any());

        Path spoolDir = tempDir.resolve("spool");
        try (var stream = Files.list(spoolDir)) {
            assertEquals(1, stream.count(), "Failed events must remain in spool for next retry cycle");
        } catch (Exception e) {
            fail("Exception while reading spool dir: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("drainOutbox preserves event files on disk when sendEvent throws network/runtime exception")
    void drainOutboxPreservesFileOnClientException() {
        UsbEventPayload p1 = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("user-exc")
                .occurredAt(Instant.now())
                .build();

        outboxSpoolService.spool(p1);

        when(adminServerClient.sendEvent(anyString(), anyString(), any(UsbEventPayload.class)))
                .thenThrow(new RuntimeException("Simulated connection reset / network timeout"));

        // drainOutbox must catch the exception safely and NOT delete the event file
        assertDoesNotThrow(() -> outboxSpoolService.drainOutbox("EP-01", "token-1", adminServerClient));

        Path spoolDir = tempDir.resolve("spool");
        try (var stream = Files.list(spoolDir)) {
            assertEquals(1, stream.count(), "Event file must NOT be deleted when sendEvent throws an exception");
        } catch (Exception e) {
            fail("Exception while checking spool directory: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("drainOutbox removes unparseable corrupted file and delivers remaining valid events")
    void drainOutboxHandlesCorruptedJsonFile() throws Exception {
        Path spoolDir = tempDir.resolve("spool");
        // Create an unparseable malformed JSON file
        Path corruptFile = spoolDir.resolve("event_1000000_corrupt.json");
        Files.writeString(corruptFile, "{ invalid json content !!!");

        // Create a valid payload
        UsbEventPayload validPayload = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("valid-user")
                .occurredAt(Instant.now())
                .build();
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        outboxSpoolService.spool(validPayload);

        when(adminServerClient.sendEvent(anyString(), anyString(), any(UsbEventPayload.class)))
                .thenReturn(true);

        outboxSpoolService.drainOutbox("EP-01", "token-1", adminServerClient);

        // Corrupted file deleted, valid file sent and deleted
        assertFalse(Files.exists(corruptFile), "Corrupted file must be cleaned up");
        verify(adminServerClient, times(1)).sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class));

        try (var stream = Files.list(spoolDir)) {
            assertEquals(0, stream.count(), "All files should be processed and cleared");
        }
    }

    @Test
    @DisplayName("drainOutbox reloads and drains existing spool files across service restart")
    void drainOutboxSurvivesRestart() {
        UsbEventPayload p1 = UsbEventPayload.builder()
                .endpointId("EP-01")
                .linuxUsername("pre-restart-user")
                .occurredAt(Instant.now())
                .build();
        outboxSpoolService.spool(p1);

        // Simulate service restart by constructing a new OutboxSpoolService instance with the same dataDir
        AgentProperties restartProps = new AgentProperties();
        restartProps.setDataDir(tempDir.toString());
        OutboxSpoolService restartedService = new OutboxSpoolService(restartProps, objectMapper);
        restartedService.init();

        when(adminServerClient.sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class)))
                .thenReturn(true);

        restartedService.drainOutbox("EP-01", "token-1", adminServerClient);

        ArgumentCaptor<UsbEventPayload> captor = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(adminServerClient).sendEvent(eq("EP-01"), eq("token-1"), captor.capture());
        assertEquals("pre-restart-user", captor.getValue().getLinuxUsername());

        Path spoolDir = tempDir.resolve("spool");
        try (var stream = Files.list(spoolDir)) {
            assertEquals(0, stream.count(), "Spooled file must be drained after restart");
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("spool returns false gracefully on disk write failure")
    void spoolHandlesDiskWriteFailure() {
        AgentProperties invalidProps = new AgentProperties();
        invalidProps.setDataDir("Z:\\non_existent_drive_path_xyz");
        OutboxSpoolService brokenService = new OutboxSpoolService(invalidProps, objectMapper);
        // Do not call init() or let init point to fallback, or point spoolPath to null
        assertFalse(brokenService.spool(UsbEventPayload.builder().endpointId("EP-01").build()));
    }
}
