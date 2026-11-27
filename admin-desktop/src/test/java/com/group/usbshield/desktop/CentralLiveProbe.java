package com.group.usbshield.desktop;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

public final class CentralLiveProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        var config = DesktopConfiguration.environment();
        var agentOrigin = URI.create(System.getenv("P2_TEST_AGENT_ORIGIN"));
        try (var desktop = config.connect();
             var agent = HttpClient.newBuilder().sslContext(CentralTls.trust(config.ca()))
                     .proxy(ProxySelector.of(null)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            require("p2-synthetic-admin".equals(desktop.login("p2-synthetic-admin",
                    System.getenv("P2_TEST_ADMIN_PASSWORD")).username()), "login");
            var install = UUID.randomUUID();
            String endpoint = desktop.allocateSlot(install, "p2-fixture-host").endpointId();
            var issued = desktop.issueTicket(endpoint);
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            post(agent, agentOrigin, "/api/agent/enroll", null, null, Map.of(
                    "installationId", install, "requestId", UUID.randomUUID(),
                    "machineId", "p2-synthetic-" + install, "hostname", "p2-fixture-host", "enrollmentToken", issued.enrollmentToken(), "agentToken", token));
            post(agent, agentOrigin, "/api/agent/heartbeat", endpoint, token, Map.of("endpointId", endpoint));
            postEvent(agent, agentOrigin, endpoint, token, install, Instant.parse("2025-10-15T12:00:00Z"));
            require("ONLINE".equals(desktop.endpoint(endpoint).status()), "endpoint status");
            require(desktop.endpoints(0, "p2-fixture", "online").totalElements() > 0, "endpoint listing");
            require(desktop.dashboard(Instant.EPOCH, Instant.now().plusSeconds(2)).summary().totalDevices() > 0, "dashboard");
            var devices = desktop.devices(0, "", endpoint, "blocked");
            require(devices.content().size() == 1, "observed device");
            desktop.allow(endpoint, devices.content().getFirst().usbDeviceId());
            var whitelist = desktop.whitelist(0, "", endpoint);
            require(whitelist.content().size() == 1, "allow mutation");
            var policyRequest = HttpRequest.newBuilder(agentOrigin.resolve("/api/agent/policy"))
                    .header("X-USBShield-Protocol", "2").header("X-Endpoint-Id", endpoint).header("X-Agent-Token", token)
                    .timeout(java.time.Duration.ofSeconds(8)).GET().build();
            var policy = agent.send(policyRequest, HttpResponse.BodyHandlers.ofByteArray());
            require(policy.statusCode() == 200, "policy pull");
            long desired = JSON.readTree(policy.body()).required("policyVersion").asLong();
            post(agent, agentOrigin, "/api/agent/policy/ack", endpoint, token, Map.of("appliedPolicyVersion", desired));
            require(desktop.endpoint(endpoint).appliedPolicyVersion() == desired, "ACK visible in Desktop");
            desktop.revoke(endpoint, whitelist.content().getFirst().id());
            require(desktop.whitelist(0, "", endpoint).content().isEmpty(), "revoke mutation");
            var events = desktop.events(0, Map.of("endpointId", endpoint));
            require(events.content().size() == 1 && "BLOCKED".equals(desktop.event(events.content().getFirst().id()).decision()), "event queries");
            verifyEventDateBoundary(desktop, agent, agentOrigin, endpoint, token, install);
            require(desktop.audit(0).totalElements() > 0, "audit");
            desktop.revokeCredential(endpoint);
            desktop.logout();
            try {
                desktop.endpoint(endpoint);
                throw new IllegalStateException("logout left a usable session");
            } catch (CentralClient.RequestFailure expected) {
                require(expected.status() == 401, "logout status");
            }
            System.out.println("PASS Desktop HTTPS consumer against packaged Central and synthetic Agent on native Nginx");
        }
    }
    private static void verifyEventDateBoundary(CentralClient desktop, HttpClient agent, URI agentOrigin,
                                                String endpoint, String token, UUID install) throws Exception {
        var date = LocalDate.of(2025, 10, 16);
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var lastMicrosecond = Instant.parse("2025-10-16T16:59:59.999999Z");
        var nextMidnight = Instant.parse("2025-10-16T17:00:00Z");
        postEvent(agent, agentOrigin, endpoint, token, install, lastMicrosecond);
        postEvent(agent, agentOrigin, endpoint, token, install, nextMidnight);
        var allEvents = desktop.events(0, Map.of("endpointId", endpoint));
        require(allEvents.content().stream().anyMatch(event -> nextMidnight.equals(Instant.parse(event.occurredAt()))),
                "next-midnight event persisted before applying the filter");
        var filtered = desktop.events(0, Map.of("endpointId", endpoint,
                "from", date.atStartOfDay(zone).toInstant().toString(),
                "to", DesktopController.inclusiveEventEnd(date, zone).toString()));
        require(filtered.content().size() == 1 && lastMicrosecond.equals(Instant.parse(filtered.content().getFirst().occurredAt())),
                "inclusive event end retains the final microsecond and excludes next midnight");
        System.out.println("PASS event date boundary against packaged Central and PostgreSQL");
    }

    private static void postEvent(HttpClient agent, URI agentOrigin, String endpoint, String token,
                                  UUID install, Instant occurredAt) throws Exception {
        post(agent, agentOrigin, "/api/agent/events", endpoint, token, Map.of(
                "endpointId", endpoint, "eventId", UUID.randomUUID(), "hostname", "p2-fixture-host",
                "eventType", "CONNECTED", "decision", "BLOCKED", "occurredAt", occurredAt.toString(),
                "device", Map.of("vendorId", "0951", "productId", "1666", "name", "P2 Synthetic USB", "hash", "p2-hash-" + install)));
    }

    private static void post(HttpClient client, URI origin, String path, String endpoint, String token,
                             Map<String, Object> body) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(java.time.Duration.ofSeconds(8))
                .header("X-USBShield-Protocol", "2").header("Content-Type", "application/json");
        if (token != null) request.header("X-Endpoint-Id", endpoint).header("X-Agent-Token", token);
        int status = client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        require(status == 200, path + " HTTP " + status);
    }
    private static void require(boolean condition, String subject) {
        if (!condition) throw new IllegalStateException("Fixture failed: " + subject);
    }
}
