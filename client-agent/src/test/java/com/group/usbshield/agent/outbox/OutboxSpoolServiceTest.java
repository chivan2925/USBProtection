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
    @DisplayName("drainOutbox should deliver spooled events in order and delete delivered files")
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
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        outboxSpoolService.spool(p2);

        when(adminServerClient.sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class)))
                .thenReturn(true);

        outboxSpoolService.drainOutbox("EP-01", "token-1", adminServerClient);

        verify(adminServerClient, times(2)).sendEvent(eq("EP-01"), eq("token-1"), any(UsbEventPayload.class));

        Path spoolDir = tempDir.resolve("spool");
        try (var stream = Files.list(spoolDir)) {
            assertEquals(0, stream.count(), "All spooled files should have been removed after successful delivery");
        } catch (Exception e) {
            fail("Exception while reading spool dir: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("drainOutbox pauses on network error without dropping files")
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
}
