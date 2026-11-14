package com.group.usbshield.agent.event;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.endpoint.EndpointRegistration;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.system.SystemInfoProvider;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DurableAsyncDispatchRegressionTest {
    @Test void slowHttpWorkerCannotBlockUsbGuardEventReaderAndIdsAreStable() throws Exception {
        EndpointManager registration = mock(EndpointManager.class);
        var server = mock(AdminServerClient.class);
        var spool = mock(OutboxSpoolService.class);
        when(registration.getCurrentRegistration()).thenReturn(
                EndpointRegistration.builder().endpointId("EP-A").agentToken("lab-token").build());
        when(spool.spool(any())).thenReturn(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return null;
        }).when(spool).drainOutbox(eq("EP-A"), eq("lab-token"), same(server));
        EventSenderService sender = new EventSenderService(registration, mock(ActiveUserResolver.class),
                mock(SystemInfoProvider.class), server, spool);
        try {
            UsbEventPayload first = event();
            sender.enqueuePayload(first);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            // The slow worker is now blocked, but this new event must still be
            // durably spooled instead of performing HTTP on the watch thread.
            UsbEventPayload second = event();
            sender.enqueuePayload(second);
            verify(spool, times(2)).spool(any());
            assertNotNull(first.getEventId());
            assertNotNull(second.getEventId());
            assertNotEquals(first.getEventId(), second.getEventId());
            verify(server, never()).sendEvent(any(), any(), any());
        } finally {
            release.countDown();
            sender.stopTransportWorker();
        }
    }

    private static UsbEventPayload event() {
        return UsbEventPayload.builder().endpointId("EP-A")
                .eventType("CONNECTED").decision("BLOCKED")
                .device(Map.of("hash", "hash", "vendorId", "0951", "productId", "1665"))
                .occurredAt(Instant.now()).build();
    }
}
