package com.group.usbshield.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxConcurrentDrainRegressionTest {
    @TempDir Path temporaryDirectory;

    private UsbEventPayload event() {
        return UsbEventPayload.builder().endpointId("EP-A")
                .eventId(UUID.randomUUID().toString())
                .eventType("CONNECTED").decision("BLOCKED")
                .device(Map.of("hash", "KingstonHash", "vendorId", "0951", "productId", "1665"))
                .occurredAt(Instant.now()).build();
    }

    @Test
    void blockedHttpDrainDoesNotBlockConcurrentDurableSpoolWrite() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setDataDir(temporaryDirectory.toString());
        OutboxSpoolService spool = new OutboxSpoolService(properties, new ObjectMapper().findAndRegisterModules());
        spool.init();
        assertTrue(spool.spool(event()));
        AdminServerClient http = mock(AdminServerClient.class);
        CountDownLatch enteredHttp = new CountDownLatch(1);
        CountDownLatch finishHttp = new CountDownLatch(1);
        when(http.sendEvent(eq("EP-A"), eq("token"), any())).thenAnswer(invocation -> {
            enteredHttp.countDown();
            assertTrue(finishHttp.await(5, TimeUnit.SECONDS));
            return true;
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            var draining = threads.submit(() -> spool.drainOutbox("EP-A", "token", http));
            assertTrue(enteredHttp.await(2, TimeUnit.SECONDS));
            var writing = threads.submit(() -> spool.spool(event()));
            // If drain synchronizes on the same monitor as spool, this times out.
            assertTrue(writing.get(2, TimeUnit.SECONDS));
            finishHttp.countDown();
            draining.get(3, TimeUnit.SECONDS);
        } finally {
            finishHttp.countDown();
            threads.shutdownNow();
        }
    }
}
