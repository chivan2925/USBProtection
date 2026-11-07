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
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPermanentRejectionRegressionTest {
    @TempDir Path temp;
    private UsbEventPayload payload(String username) {
        return UsbEventPayload.builder().endpointId("EP-A").linuxUsername(username)
                .eventType("CONNECTED").decision("BLOCKED").occurredAt(Instant.now()).build();
    }
    @Test void permanentlyRejectedHeadIsQuarantinedWithoutBlockingNextEvent() throws Exception {
        var props = new AgentProperties(); props.setDataDir(temp.toString());
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var spool = new OutboxSpoolService(props, mapper); spool.init();
        assertTrue(spool.spool(payload("bad")));
        assertTrue(spool.spool(payload("good")));
        var client = mock(AdminServerClient.class);
        when(client.sendEvent(eq("EP-A"), eq("token"), any())).thenReturn(false, true);
        when(client.lastEventWasPermanentlyRejected()).thenReturn(true);
        spool.drainOutbox("EP-A", "token", client);
        verify(client, times(2)).sendEvent(eq("EP-A"), eq("token"), any());
        try (var quarantined = Files.list(temp.resolve("spool/quarantine"))) {
            assertEquals(1L, quarantined.count());
        }
        try (var all = Files.list(temp.resolve("spool"))) {
            assertEquals(1L, all.count()); // Only quarantine directory.
        }
    }
    @Test void transientRejectionRetainsHeadAndStopsFifo() throws Exception {
        var props = new AgentProperties(); props.setDataDir(temp.toString());
        var spool = new OutboxSpoolService(props,
                new ObjectMapper().registerModule(new JavaTimeModule())); spool.init();
        assertTrue(spool.spool(payload("first")));
        assertTrue(spool.spool(payload("second")));
        var client = mock(AdminServerClient.class);
        when(client.sendEvent(anyString(), anyString(), any())).thenReturn(false);
        spool.drainOutbox("EP-A", "token", client);
        verify(client, times(1)).sendEvent(anyString(), anyString(), any());
        try (var all = Files.list(temp.resolve("spool"))) {
            assertEquals(2L, all.count());
        }
    }
}
