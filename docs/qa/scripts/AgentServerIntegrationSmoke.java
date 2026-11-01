// QA-only: built production Agent client -> real packaged Spring Admin Server.
// Credentials and payload markers stay in memory; the result contains fixed labels only.
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.sync.dto.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class AgentServerIntegrationSmoke {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient raw = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final HttpClient admin = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final List<Map<String, Object>> scenarios = new ArrayList<>();
    private final String bootstrap = requiredEnv("QA_INTEGRATION_BOOTSTRAP");
    private final String adminUser = requiredEnv("QA_INTEGRATION_ADMIN_USERNAME");
    private final String adminPassword = requiredEnv("QA_INTEGRATION_ADMIN_PASSWORD");
    private final String marker = requiredEnv("QA_INTEGRATION_LOG_MARKER");
    private final String username = marker + "-user";
    private final String session = marker + "-session";
    private final String serial = marker + "-serial";
    private final String deviceHash = marker + "-hash";
    private final Instant sourceTime = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
    private int assertions;
    private int requests;
    private String base;
    private String csrfHeader;
    private String csrfToken;
    private String endpointA;
    private String endpointB;
    private String tokenA;
    private String tokenB;
    private AdminServerClient agent;
    @FunctionalInterface private interface Action { void run() throws Exception; }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required QA environment missing");
        return value;
    }
    private void check(String label, boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError(label); // Fixed labels; no raw response/secret.
    }
    private String route(String path) {
        return path.split("\\?", 2)[0].replaceAll("/EP-[0-9a-f-]+", "/{endpointId}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }
    private void scenario(String name, String method, String path, String transport, Action action) throws Exception {
        int start = assertions;
        var record = new LinkedHashMap<String, Object>();
        record.put("name", name);
        record.put("method", method);
        record.put("route", route(path));
        record.put("transport", transport);
        try { action.run(); record.put("status", "PASS"); }
        catch (Throwable failure) {
            record.put("status", "FAIL");
            record.put("error", failure instanceof AssertionError ? failure.getMessage() : "Integration operation failed");
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException("Integration operation failed");
        } finally { record.put("assertionCount", assertions - start); scenarios.add(record); }
    }
    private void production(String name, String method, String path, Action action) throws Exception {
        scenario(name, method, path, "AdminServerClient(AgentProperties)", () -> { requests++; action.run(); });
    }
    private JsonNode http(String name, HttpClient client, String method, String path,
                          Object body, Map<String, String> headers, int expected) throws Exception {
        int start = assertions;
        var record = new LinkedHashMap<String, Object>();
        record.put("name", name); record.put("method", method); record.put("route", route(path));
        record.put("transport", "JDK HttpClient"); record.put("expectedHttpStatus", expected);
        try {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            headers.forEach(builder::header);
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            requests++;
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            record.put("actualHttpStatus", response.statusCode());
            check(name + ": HTTP status", response.statusCode() == expected);
            check(name + ": bounded response", response.body().length() <= 1024 * 1024);
            record.put("status", "PASS");
            return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
        } catch (Throwable failure) {
            record.put("status", "FAIL");
            record.put("error", failure instanceof AssertionError ? failure.getMessage() : "HTTP request failed");
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException("HTTP request failed");
        } finally { record.put("assertionCount", assertions - start); scenarios.add(record); }
    }
    private Map<String, String> credentials(String id, String token) {
        return Map.of("X-Endpoint-Id", id, "X-Agent-Token", token);
    }
    private Map<String, String> csrf() { return Map.of(csrfHeader, csrfToken); }
    private void refreshCsrf(String name) throws Exception {
        var response = http(name, admin, "GET", "/api/auth/csrf", null, Map.of(), 200);
        csrfHeader = response.path("headerName").asText(); csrfToken = response.path("token").asText();
        check(name + ": CSRF shape", "X-CSRF-TOKEN".equals(csrfHeader) && !csrfToken.isBlank());
    }
    private String query(Map<String, String> params) {
        return params.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&", "/api/admin/events?", ""));
    }
    private JsonNode events(String name, Map<String, String> params) throws Exception {
        return http(name, admin, "GET", query(params), null, Map.of(), 200);
    }
    private Map<String, Object> device(boolean noSerial) {
        return Map.of("vendorId", "0951", "productId", "1666", "serial", noSerial ? "" : serial,
                "name", "Synthetic QA storage", "hash", noSerial ? deviceHash + "-no-serial" : deviceHash,
                "interfaceClass", "08:06:50");
    }
    private UsbEventPayload payload(String id, String user, Long uid, boolean noSerial) {
        return UsbEventPayload.builder().endpointId(id).hostname("qa-integrated-host")
                .linuxUsername(user).linuxUid(uid).sessionId(session).sessionType("wayland").seat("seat0")
                .device(device(noSerial)).eventType("CONNECTED").decision("BLOCKED").occurredAt(sourceTime).build();
    }
    private ObjectNode eventBody(String user, Long uid, boolean noSerial) {
        return json.valueToTree(payload(endpointA, user, uid, noSerial));
    }
    private boolean sameInstant(JsonNode node, Instant expected) {
        try { return expected.equals(json.treeToValue(node, Instant.class)); }
        catch (Exception invalid) { return false; }
    }
    private void canonicalEvent(String label, JsonNode event, String user, Long uid, boolean noSerial) {
        check(label + ": endpoint/hostname", endpointA.equals(event.path("endpointId").asText())
                && "qa-integrated-host".equals(event.path("hostname").asText()));
        check(label + ": user", user.equals(event.path("linuxUsername").asText()));
        check(label + ": UID", uid == null ? event.path("linuxUid").isNull() : event.path("linuxUid").asLong(-1) == uid);
        check(label + ": session", "UNKNOWN".equals(user) ? event.path("sessionId").isNull()
                : session.equals(event.path("sessionId").asText()));
        check(label + ": source Instant exact", sameInstant(event.path("occurredAt"), sourceTime));
        check(label + ": received Instant", event.path("receivedAt").isTextual()
                && sameInstant(event.path("receivedAt"), Instant.parse(event.path("receivedAt").asText())));
        check(label + ": canonical event enums", "CONNECTED".equals(event.path("eventType").asText())
                && "BLOCKED".equals(event.path("decision").asText()));
        JsonNode usb = event.path("device");
        check(label + ": device identifier", event.path("usbDeviceId").asLong() > 0
                && "0951".equals(usb.path("vendorId").asText()) && "1666".equals(usb.path("productId").asText()));
        check(label + ": serial/hash", (noSerial ? "" : serial).equals(usb.path("serial").asText())
                && (noSerial ? deviceHash + "-no-serial" : deviceHash).equals(usb.path("hash").asText()));
        String encoded = event.toString();
        check(label + ": no credentials in Admin data", !encoded.contains(tokenA) && !encoded.contains(tokenB)
                && !encoded.contains("agentTokenHash") && !encoded.contains("agentToken"));
    }
    private void policy(String label, String id, String token, long version, int count) throws Exception {
        production(label, "GET", "/api/agent/policy", () -> {
            var optional = agent.fetchPolicy(id, token);
            check(label + ": decoded", optional.isPresent());
            var value = optional.orElseThrow();
            check(label + ": version/count", value.getPolicyVersion() == version && value.getWhitelistedDevices().size() == count);
            if (count == 1) {
                var usb = value.getWhitelistedDevices().getFirst();
                check(label + ": actual Agent DTO hash/serial", deviceHash.equals(usb.getDeviceHash())
                        && deviceHash.equals(usb.getFingerprintValue()) && serial.equals(usb.getSerialNumber()));
            }
        });
    }
    private PolicyAckRequest ack(long version) {
        return PolicyAckRequest.builder().appliedPolicyVersion(version).appliedAt(sourceTime).ruleCount(1).build();
    }
    private void exercise(String url) throws Exception {
        URI uri = URI.create(url);
        check("loopback-only HTTP target", "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() > 0 && uri.getRawUserInfo() == null && (uri.getPath().isEmpty() || "/".equals(uri.getPath())));
        check("bounded QA marker", marker.matches("qa-[a-z0-9]{16,32}") && session.length() <= 50);
        base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        http("prod-api-docs-hidden", raw, "GET", "/api-docs", null, Map.of(), 404);
        http("prod-swagger-hidden", raw, "GET", "/swagger-ui.html", null, Map.of(), 404);
        http("admin-session-required", raw, "GET", "/api/admin/endpoints", null, Map.of(), 401);
        refreshCsrf("admin-csrf-before-login");
        http("admin-invalid-password", admin, "POST", "/api/auth/login", Map.of("username", adminUser, "password", UUID.randomUUID().toString()), csrf(), 401);
        http("admin-login", admin, "POST", "/api/auth/login", Map.of("username", adminUser, "password", adminPassword), csrf(), 200);
        refreshCsrf("admin-csrf-after-login");
        check("admin me identity", adminUser.equals(http("admin-me", admin, "GET", "/api/auth/me", null, Map.of(), 200).path("username").asText()));
        var properties = new AgentProperties(); properties.setServerUrl(base); properties.setEnrollmentToken(bootstrap);
        agent = new AdminServerClient(properties); // No injected RestClient or controlled server stub.
        production("production-agent-enroll-A", "POST", "/api/agent/enroll", () -> {
            var result = agent.enroll(EnrollmentRequest.builder().machineId(UUID.randomUUID().toString())
                    .hostname("qa-integrated-A").ipAddress("127.0.0.1").enrollmentToken(bootstrap).build());
            check("A enrollment decoded", result.isPresent());
            var value = result.orElseThrow(); endpointA = value.getEndpointId(); tokenA = value.getAgentToken();
            check("A credential/version shape", endpointA.startsWith("EP-") && tokenA.length() == 72 && value.getCurrentPolicyVersion() == 1);
        });
        production("production-agent-enroll-B", "POST", "/api/agent/enroll", () -> {
            var result = agent.enroll(EnrollmentRequest.builder().machineId(UUID.randomUUID().toString())
                    .hostname("qa-integrated-B").enrollmentToken(bootstrap).build());
            check("B enrollment decoded", result.isPresent());
            var value = result.orElseThrow(); endpointB = value.getEndpointId(); tokenB = value.getAgentToken();
            check("B credential isolation", !endpointA.equals(endpointB) && !tokenA.equals(tokenB) && tokenB.length() == 72 && value.getCurrentPolicyVersion() == 1);
        });
        production("production-agent-heartbeat-not-ACK", "POST", "/api/agent/heartbeat", () -> {
            var value = agent.sendHeartbeat(HeartbeatRequest.builder().endpointId(endpointA).currentPolicyVersion(999L).build(), tokenA);
            check("heartbeat decoded", value.isPresent());
            check("heartbeat telemetry does not ACK", value.orElseThrow().getLastAppliedPolicyVersion() == 0
                    && value.orElseThrow().getServerPolicyVersion() == 1);
            check("heartbeat response status is OK", "OK".equals(value.orElseThrow().getStatus()));
        });
        policy("initial-policy-A", endpointA, tokenA, 1, 0); policy("initial-policy-B", endpointB, tokenB, 1, 0);
        production("production-agent-invalid-token", "GET", "/api/agent/policy", () ->
                check("invalid policy token rejected", agent.fetchPolicy(endpointA, UUID.randomUUID().toString()).isEmpty()));
        production("production-agent-cross-endpoint-token", "GET", "/api/agent/policy", () ->
                check("A token cannot authorize B", agent.fetchPolicy(endpointB, tokenA).isEmpty()));
        http("missing-agent-token-401", raw, "GET", "/api/agent/policy", null, Map.of(), 401);
        http("invalid-agent-token-401", raw, "GET", "/api/agent/policy", null, credentials(endpointA, UUID.randomUUID().toString()), 401);
        http("cross-endpoint-token-401", raw, "GET", "/api/agent/policy", null, credentials(endpointB, tokenA), 401);
        http("agent-cannot-read-admin", raw, "GET", "/api/admin/endpoints", null, credentials(endpointA, tokenA), 401);
        http("admin-session-cannot-authorize-agent", admin, "GET", "/api/agent/policy", null, Map.of(), 401);
        production("production-agent-spoofed-event", "POST", "/api/agent/events", () ->
                check("spoofed event rejected", !agent.sendEvent(endpointA, tokenA, payload(endpointB, username, 1001L, false))));
        http("spoofed-event-403", raw, "POST", "/api/agent/events", payload(endpointB, username, 1001L, false), credentials(endpointA, tokenA), 403);
        production("production-agent-event", "POST", "/api/agent/events", () ->
                check("production event accepted", agent.sendEvent(endpointA, tokenA, payload(endpointA, username, 1001L, false))));
        var page = events("canonical-event-filtered", Map.of("endpointId", endpointA, "linuxUsername", username,
                "decision", "BLOCKED", "eventType", "CONNECTED", "from", sourceTime.minusSeconds(1).toString(),
                "to", sourceTime.plusSeconds(1).toString(), "size", "1"));
        check("single filtered production event", page.path("totalElements").asLong() == 1 && page.path("content").size() == 1);
        var event = page.path("content").get(0); canonicalEvent("production event", event, username, 1001L, false);
        long eventId = event.path("id").asLong(); long deviceId = event.path("usbDeviceId").asLong();
        canonicalEvent("event detail", http("canonical-event-detail", admin, "GET", "/api/admin/events/" + eventId, null, Map.of(), 200), username, 1001L, false);
        String whitelist = "/api/admin/endpoints/" + endpointA + "/whitelist";
        http("allow-without-csrf-403", admin, "POST", whitelist, Map.of("usbDeviceId", deviceId), Map.of(), 403);
        var entry = http("allow-A", admin, "POST", whitelist, Map.of("usbDeviceId", deviceId), csrf(), 200);
        var duplicate = http("allow-A-idempotent", admin, "POST", whitelist, Map.of("usbDeviceId", deviceId), csrf(), 200);
        check("duplicate allow same entry", entry.path("id").asLong() > 0 && entry.path("id").asLong() == duplicate.path("id").asLong());
        policy("allow-policy-A", endpointA, tokenA, 2, 1); policy("allow-leaves-policy-B", endpointB, tokenB, 1, 0);
        production("production-agent-future-ACK", "POST", "/api/agent/policy/ack", () ->
                check("future ACK rejected", !agent.acknowledgePolicy(endpointA, tokenA, ack(3))));
        http("future-ACK-409", raw, "POST", "/api/agent/policy/ack", ack(3), credentials(endpointA, tokenA), 409);
        http("negative-ACK-400", raw, "POST", "/api/agent/policy/ack", ack(-1), credentials(endpointA, tokenA), 400);
        production("production-agent-valid-ACK", "POST", "/api/agent/policy/ack", () ->
                check("valid ACK accepted", agent.acknowledgePolicy(endpointA, tokenA, ack(2))));
        production("production-agent-idempotent-ACK", "POST", "/api/agent/policy/ack", () ->
                check("idempotent ACK accepted", agent.acknowledgePolicy(endpointA, tokenA, ack(2))));
        production("production-agent-heartbeat-after-ACK", "POST", "/api/agent/heartbeat", () -> {
            var value = agent.sendHeartbeat(HeartbeatRequest.builder().endpointId(endpointA).currentPolicyVersion(999L).build(), tokenA);
            check("applied remains acknowledged version", value.isPresent() && value.orElseThrow().getLastAppliedPolicyVersion() == 2
                    && value.orElseThrow().getServerPolicyVersion() == 2);
        });
        http("stale-ACK-409", raw, "POST", "/api/agent/policy/ack", ack(1), credentials(endpointA, tokenA), 409);
        http("cross-endpoint-revoke-404", admin, "DELETE", "/api/admin/endpoints/" + endpointB + "/whitelist/" + entry.path("id").asLong(), null, csrf(), 404);
        http("revoke-A", admin, "DELETE", whitelist + "/" + entry.path("id").asLong(), null, csrf(), 204);
        http("revoke-A-idempotent", admin, "DELETE", whitelist + "/" + entry.path("id").asLong(), null, csrf(), 204);
        policy("revoke-policy-A", endpointA, tokenA, 3, 0); policy("revoke-leaves-policy-B", endpointB, tokenB, 1, 0);
        production("production-agent-revoke-ACK", "POST", "/api/agent/policy/ack", () ->
                check("revoke ACK accepted", agent.acknowledgePolicy(endpointA, tokenA, ack(3))));
        var registryA = http("registry-A", admin, "GET", "/api/admin/endpoints/" + endpointA, null, Map.of(), 200);
        var registryB = http("registry-B", admin, "GET", "/api/admin/endpoints/" + endpointB, null, Map.of(), 200);
        check("A desired/applied after revoke", registryA.path("policyVersion").asLong() == 3 && registryA.path("appliedPolicyVersion").asLong() == 3);
        check("B desired/applied unchanged", registryB.path("policyVersion").asLong() == 1 && registryB.path("appliedPolicyVersion").asLong() == 0);
        check("registry liveness is distinct from heartbeat response", "ONLINE".equals(registryA.path("status").asText())
                && "OFFLINE".equals(registryB.path("status").asText()));
        check("registry never exposes credential hash", !registryA.has("agentTokenHash") && !registryB.has("agentTokenHash"));

        http("bootstrap-257-401", raw, "POST", "/api/agent/enroll", Map.of("enrollmentToken", "x".repeat(257), "machineId", UUID.randomUUID().toString(), "hostname", "qa-invalid"), Map.of(), 401);
        http("enrollment-IP-51-400", raw, "POST", "/api/agent/enroll", Map.of("enrollmentToken", bootstrap, "machineId", UUID.randomUUID().toString(), "hostname", "qa-invalid", "ipAddress", "1".repeat(51)), Map.of(), 400);
        http("UTF8-token-over-72-bytes-401", raw, "GET", "/api/agent/policy", null, credentials(endpointA, "é".repeat(72)), 401);
        String hundred = username + "x".repeat(100 - username.length());
        http("username-100-UID-zero-accepted", raw, "POST", "/api/agent/events", eventBody(hundred, 0L, true), credentials(endpointA, tokenA), 200);
        http("username-101-rejected-400", raw, "POST", "/api/agent/events", eventBody(hundred + "x", 1001L, false), credentials(endpointA, tokenA), 400);
        http("negative-UID-rejected-400", raw, "POST", "/api/agent/events", eventBody(username, -1L, false), credentials(endpointA, tokenA), 400);
        http("nullable-UID-accepted", raw, "POST", "/api/agent/events", eventBody(username + "-null", null, false), credentials(endpointA, tokenA), 200);
        http("UNKNOWN-normalization", raw, "POST", "/api/agent/events", eventBody("UNKNOWN", 1001L, false), credentials(endpointA, tokenA), 200);
        http("blank-user-normalization", raw, "POST", "/api/agent/events", eventBody(" ", 1001L, false), credentials(endpointA, tokenA), 200);
        var absent = eventBody(username, 1001L, false); absent.remove("linuxUsername");
        http("absent-user-normalization", raw, "POST", "/api/agent/events", absent, credentials(endpointA, tokenA), 200);
        var hundredPage = events("username-100-history", Map.of("endpointId", endpointA, "linuxUsername", hundred));
        check("username100 single history row", hundredPage.path("content").size() == 1);
        canonicalEvent("username100 UID0 no-serial", hundredPage.path("content").get(0), hundred, 0L, true);
        var nullablePage = events("nullable-UID-history", Map.of("endpointId", endpointA, "linuxUsername", username + "-null"));
        check("nullable UID single history row", nullablePage.path("content").size() == 1);
        canonicalEvent("nullable UID", nullablePage.path("content").get(0), username + "-null", null, false);
        var unknownPage = events("UNKNOWN-history", Map.of("endpointId", endpointA, "linuxUsername", "UNKNOWN"));
        check("all UNKNOWN representations persisted", unknownPage.path("content").size() == 3);
        for (JsonNode unknown : unknownPage.path("content")) canonicalEvent("UNKNOWN history", unknown, "UNKNOWN", null, false);
        long previousId = Long.MAX_VALUE;
        for (int index = 0; index < 3; index++) {
            var history = events("paged-history-" + index, Map.of("endpointId", endpointA, "page", Integer.toString(index), "size", "2"));
            check("history rejected events not persisted", history.path("totalElements").asLong() == 6 && history.path("totalPages").asInt() == 3
                    && history.path("page").asInt() == index && history.path("content").size() == 2);
            for (JsonNode item : history.path("content")) {
                check("history stable descending tie-break", item.path("id").asLong() < previousId && sameInstant(item.path("occurredAt"), sourceTime));
                previousId = item.path("id").asLong();
            }
        }
        check("B receives no A event history", events("B-history-isolated", Map.of("endpointId", endpointB)).path("totalElements").asLong() == 0);
        http("admin-logout", admin, "POST", "/api/auth/logout", Map.of(), csrf(), 200);
        http("logout-invalidates-session", admin, "GET", "/api/auth/me", null, Map.of(), 401);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected loopback URL and result path");
        var smoke = new AgentServerIntegrationSmoke();
        var result = new LinkedHashMap<String, Object>();
        result.put("startedAt", Instant.now().toString());
        result.put("constructor", "AdminServerClient(AgentProperties)");
        result.put("scope", "Built production Agent client against real packaged Spring Admin Server/prod profile over loopback; synthetic identities only. No daemon, durable retry, native IPC, GUI, Ubuntu or physical USB execution.");
        int exit = 0;
        try { smoke.exercise(args[0]); result.put("status", "PASS"); }
        catch (Throwable failure) {
            exit = 1; result.put("status", "FAIL");
            result.put("error", failure instanceof AssertionError ? failure.getMessage() : "Integration smoke failed: " + failure.getClass().getSimpleName());
        } finally {
            result.put("endedAt", Instant.now().toString()); result.put("assertionCount", smoke.assertions);
            result.put("requestCount", smoke.requests); result.put("requestCountScope", "Harness initiated HTTP operations, including production-client calls; not a server access-log counter");
            result.put("scenarioCount", smoke.scenarios.size()); result.put("scenarios", smoke.scenarios);
            result.put("sourceInstant", smoke.sourceTime.toString());
            Files.writeString(Path.of(args[1]), smoke.json.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
        }
        System.out.println("AGENT_SERVER_SMOKE " + result.get("status") + "; scenarios=" + smoke.scenarios.size() + "; assertions=" + smoke.assertions);
        if (exit != 0) System.exit(exit);
    }
}
