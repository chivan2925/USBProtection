package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cached accounting must preserve quota rejection and external evidence detection. */
class OutboxUsageCacheRegressionTest {
    @TempDir Path temp;

    private OutboxSpoolService create(int maxFiles) {
        AgentProperties props = new AgentProperties();
        props.setDataDir(temp.toString());
        props.setOutboxMaxEvents(maxFiles);
        props.setOutboxMaxBytes(10_000_000);
        OutboxSpoolService service = new OutboxSpoolService(props,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        service.init();
        return service;
    }

    private static UsbEventPayload event(String name) {
        return UsbEventPayload.builder().endpointId("EP-A").linuxUsername(name)
                .eventType("CONNECTED").decision("BLOCKED")
                .occurredAt(Instant.parse("2026-11-03T04:00:00Z")).build();
    }

    @Test void manyAppendsDoNotRepeatedlyWalkSpoolAndQuotaStillRejects() throws Exception {
        OutboxSpoolService service = create(30);
        assertEquals(1L, service.usageInspectionCount(), "initial inventory scan");
        for (int i = 0; i < 30; i++) assertTrue(service.spool(event("user-" + i)));
        assertEquals(1L, service.usageInspectionCount(), "no O(backlog) rescan on each enqueue");
        assertFalse(service.spool(event("overflow")));
        try (var files = Files.list(temp.resolve("spool"))) {
            assertEquals(30L, files.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
    }

    @Test void outOfBandFilesInvalidateCacheAndCountTowardQuota() throws Exception {
        OutboxSpoolService service = create(2);
        assertTrue(service.spool(event("saved")));
        Path spool = temp.resolve("spool");
        Files.writeString(spool.resolve("operator-evidence.json"), "{}", java.nio.charset.StandardCharsets.UTF_8);
        // Force a detectable directory change even on coarse timestamp filesystems.
        Files.setLastModifiedTime(spool, FileTime.fromMillis(System.currentTimeMillis() + 2000));
        assertFalse(service.spool(event("too-many")));
        assertTrue(service.usageInspectionCount() >= 2);
    }

    @Test void deliveredEventsFreeCapacityOnNextWriteWithoutDroppingEvidence() throws Exception {
        OutboxSpoolService service = create(1);
        assertTrue(service.spool(event("first")));
        AdminServerClient client = mock(AdminServerClient.class);
        when(client.sendEvent(eq("EP-A"), eq("token"), any())).thenReturn(true);
        service.drainOutbox("EP-A", "token", client);
        assertTrue(service.spool(event("second")), "deletion must invalidate cached quota");
        verify(client, times(1)).sendEvent(eq("EP-A"), eq("token"), any());
    }
}
