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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
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

        assertTrue(usbGuardGateway.isAvailable(), "UsbGuardGateway mock should report available");
        assertNotNull(activeUserResolver.resolveCurrentActiveUser(), "ActiveUserResolver should resolve mock active user");
    }
}
