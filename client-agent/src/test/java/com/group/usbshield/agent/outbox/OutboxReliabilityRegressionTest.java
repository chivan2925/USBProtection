package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** R07/R08/R12/R13: no wrong-identity delivery, unbounded backlog, FIFO reversal, or lost evidence. */
class OutboxReliabilityRegressionTest {
    @TempDir Path dir;

    private AgentProperties properties(int maxEvents, long maxBytes) {
        AgentProperties p = new AgentProperties();
        p.setDataDir(dir.toString());
        p.setOutboxMaxEvents(maxEvents);
        p.setOutboxMaxBytes(maxBytes);
        return p;
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static UsbEventPayload event(String endpoint, String name) {
        return UsbEventPayload.builder().endpointId(endpoint).linuxUsername(name)
                .eventType("CONNECTED").decision("BLOCKED")
                .occurredAt(Instant.parse("2026-11-03T03:00:00Z")).build();
    }

    private static List<Path> queued(Path dir) throws Exception {
        try (var list = Files.list(dir.resolve("spool"))) {
            return list.filter(p -> p.getFileName().toString().startsWith("event_"))
                    .sorted().toList();
        }
    }

    @Test void eventQuotaRejectsWithoutEvictionOrOverwrite() throws Exception {
        OutboxSpoolService outbox = new OutboxSpoolService(properties(2, 1_000_000), mapper());
        outbox.init();
        assertTrue(outbox.spool(event("EP-A", "first")));
        assertTrue(outbox.spool(event("EP-A", "second")));
        List<Path> before = queued(dir);
        assertEquals(2, before.size());
        String first = Files.readString(before.get(0));
        assertFalse(outbox.spool(event("EP-A", "third")));
        assertEquals(before, queued(dir));
        assertEquals(first, Files.readString(before.get(0)));
    }

    @Test void byteQuotaRejectsButPreservesExistingAudit() throws Exception {
        UsbEventPayload first = event("EP-A", "first");
        first.setEventId("fixed-event-id-1234567890123456789012");
        long firstBytes = mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(first).length;
        OutboxSpoolService outbox = new OutboxSpoolService(properties(100, firstBytes), mapper());
        outbox.init();
        assertTrue(outbox.spool(first));
        assertFalse(outbox.spool(event("EP-A", "second")));
        assertEquals(1, queued(dir).size());
    }

    @Test void sequentialWritesAndRestartPreserveStrictFilenameFifo() throws Exception {
        AgentProperties p = properties(100, 1_000_000);
        OutboxSpoolService first = new OutboxSpoolService(p, mapper());
        first.init();
        for (int i=0; i<20; i++) assertTrue(first.spool(event("EP-A", "user-" + i)));
        OutboxSpoolService restarted = new OutboxSpoolService(p, mapper());
        restarted.init();
        assertTrue(restarted.spool(event("EP-A", "user-20")));
        AdminServerClient client = mock(AdminServerClient.class);
        when(client.sendEvent(eq("EP-A"), eq("token"), any())).thenReturn(true);
        restarted.drainOutbox("EP-A", "token", client);
        ArgumentCaptor<UsbEventPayload> payloads = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(client, times(21)).sendEvent(eq("EP-A"), eq("token"), payloads.capture());
        for (int i=0; i<21; i++) assertEquals("user-" + i, payloads.getAllValues().get(i).getLinuxUsername());
        assertTrue(queued(dir).isEmpty());
    }

    @Test void foreignAndCorruptedEvidenceDoNotBlockLaterCurrentEndpointEvent() throws Exception {
        OutboxSpoolService outbox = new OutboxSpoolService(properties(100, 1_000_000), mapper());
        outbox.init();
        assertTrue(outbox.spool(event("EP-OLD", "foreign")));
        Path corrupt = dir.resolve("spool/event_1000000_corrupt.json");
        Files.writeString(corrupt, "{ damaged JSON");
        assertTrue(outbox.spool(event("EP-NEW", "current")));
        AdminServerClient client = mock(AdminServerClient.class);
        when(client.sendEvent(eq("EP-NEW"), eq("new-token"), any())).thenReturn(true);
        outbox.drainOutbox("EP-NEW", "new-token", client);
        ArgumentCaptor<UsbEventPayload> capture = ArgumentCaptor.forClass(UsbEventPayload.class);
        verify(client, times(1)).sendEvent(eq("EP-NEW"), eq("new-token"), capture.capture());
        assertEquals("current", capture.getValue().getLinuxUsername());
        assertTrue(queued(dir).isEmpty());
        try (var stream = Files.list(dir.resolve("spool/quarantine"))) {
            List<Path> evidence = stream.toList();
            assertEquals(2, evidence.size());
            assertTrue(evidence.stream().anyMatch(f -> f.getFileName().toString().contains("corrupt")));
        }
    }

    @Test void quarantineMoveFailurePreservesActiveQueueAndStopsDelivery() throws Exception {
        OutboxSpoolService outbox = new OutboxSpoolService(properties(100, 1_000_000), mapper());
        outbox.init();
        assertTrue(outbox.spool(event("EP-OLD", "foreign")));
        assertTrue(outbox.spool(event("EP-NEW", "must-wait")));
        // A regular file at the expected directory path makes quarantine fail.
        Files.writeString(dir.resolve("spool/quarantine"), "blocked directory");
        AdminServerClient client = mock(AdminServerClient.class);
        outbox.drainOutbox("EP-NEW", "token", client);
        assertEquals(2, queued(dir).size(), "No audit may be lost on a failed quarantine move");
        verify(client, never()).sendEvent(anyString(), anyString(), any());
    }

    @Test void quarantinedEvidenceConsumesQuotaUntilOperatorIntervention() throws Exception {
        OutboxSpoolService outbox = new OutboxSpoolService(properties(1, 1_000_000), mapper());
        outbox.init();
        assertTrue(outbox.spool(event("EP-OLD", "foreign")));
        outbox.drainOutbox("EP-NEW", "token", mock(AdminServerClient.class));
        assertFalse(outbox.spool(event("EP-NEW", "new")),
                "Quarantine must not become an unbounded bypass around disk quota");
    }
}
