package com.group.usbshield.agent;

import com.group.usbshield.agent.endpoint.EndpointManager;
import com.group.usbshield.agent.event.EventSenderService;
import com.group.usbshield.agent.outbox.OutboxSpoolService;
import com.group.usbshield.agent.session.ActiveUserResolver;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.sync.HeartbeatCoordinator;
import com.group.usbshield.agent.sync.PolicySyncService;
import com.group.usbshield.agent.usbguard.UsbGuardGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DirtiesContext
class ClientAgentTest {

    @Autowired
    private AdminServerClient adminServerClient;

    @Autowired
    private EndpointManager endpointManager;

    @Autowired
    private HeartbeatCoordinator heartbeatCoordinator;

    @Autowired
    private PolicySyncService policySyncService;

    @Autowired
    private EventSenderService eventSenderService;

    @Autowired
    private OutboxSpoolService outboxSpoolService;

    @Autowired
    private UsbGuardGateway usbGuardGateway;

    @Autowired
    private ActiveUserResolver activeUserResolver;

    @Test
    void contextLoadsAndAllBeansAreConfigured() {
        assertNotNull(adminServerClient, "AdminServerClient bean must be present");
        assertNotNull(endpointManager, "EndpointManager bean must be present");
        assertNotNull(heartbeatCoordinator, "HeartbeatCoordinator bean must be present");
        assertNotNull(policySyncService, "PolicySyncService bean must be present");
        assertNotNull(eventSenderService, "EventSenderService bean must be present");
        assertNotNull(outboxSpoolService, "OutboxSpoolService bean must be present");
        assertNotNull(usbGuardGateway, "UsbGuardGateway bean must be present");
        assertNotNull(activeUserResolver, "ActiveUserResolver bean must be present");

        // Member 3 context smoke test: verify dependency injection, not USBGuard daemon health.
        // Member 2 now supplies a real @Primary gateway, which correctly reports unavailable
        // when USBGuard is not installed (for example, on Windows development machines).
        // Real USBGuard availability/enforcement belongs to Ubuntu hardware integration tests.
        assertNotNull(activeUserResolver.resolveCurrentActiveUser(), "ActiveUserResolver should resolve mock active user");
    }
}
