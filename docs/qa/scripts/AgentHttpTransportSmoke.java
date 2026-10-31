// QA-only real HTTP exercise of the production AdminServerClient constructor.
// Launched by run-agent-http-smoke.py against classes/libs from the built Agent JAR.
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group.usbshield.agent.config.AgentProperties;
import com.group.usbshield.agent.event.UsbEventPayload;
import com.group.usbshield.agent.sync.AdminServerClient;
import com.group.usbshield.agent.sync.dto.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class AgentHttpTransportSmoke {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AtomicInteger assertions = new AtomicInteger();
    private final AtomicInteger received = new AtomicInteger();
    private final AtomicReference<Expected> expected = new AtomicReference<>();
    private final AtomicReference<String> handlerFailure = new AtomicReference<>();
    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final Map<String, String> instantWireTypes = new LinkedHashMap<>();
    private final String endpoint = "QA-HTTP-" + UUID.randomUUID();
    private final String machine = UUID.randomUUID().toString();
    private final String enrollmentToken = UUID.randomUUID().toString();
    private final String agentToken = UUID.randomUUID().toString();
    private final String rejectedToken = UUID.randomUUID().toString();
    private final Instant occurredAt = Instant.parse("2026-10-31T12:00:00.123456Z");
    private record Expected(String name, String method, String path, int auth, int status,
                            Consumer<JsonNode> body, Map<String, Object> response) {}

    private void check(String label, boolean condition) {
        assertions.incrementAndGet();
        if (!condition) throw new AssertionError(label); // Never include raw request/token values.
    }

    private void handle(HttpExchange exchange) {
        Expected item = expected.getAndSet(null);
        try {
            received.incrementAndGet();
            check("unexpected request", item != null);
            check(item.name() + ": loopback peer", exchange.getRemoteAddress().getAddress().isLoopbackAddress());
            check(item.name() + ": method", item.method().equals(exchange.getRequestMethod()));
            check(item.name() + ": path", item.path().equals(exchange.getRequestURI().getPath()));
            check(item.name() + ": no query", exchange.getRequestURI().getRawQuery() == null);
            String token = exchange.getRequestHeaders().getFirst("X-Agent-Token");
            String endpointHeader = exchange.getRequestHeaders().getFirst("X-Endpoint-Id");
            if (item.auth() == 0) {
                check(item.name() + ": no endpoint credential before enrollment", token == null && endpointHeader == null);
            } else {
                check(item.name() + ": endpoint header", endpoint.equals(endpointHeader));
                check(item.name() + ": token header", (item.auth() == 1 ? agentToken : rejectedToken).equals(token));
            }
            byte[] body = exchange.getRequestBody().readNBytes(65537);
            check(item.name() + ": bounded body", body.length <= 65536);
            if ("GET".equals(item.method())) {
                check(item.name() + ": empty GET body", body.length == 0);
                item.body().accept(json.createObjectNode());
            } else {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                check(item.name() + ": JSON content type", type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"));
                JsonNode tree = json.readTree(body);
                check(item.name() + ": JSON object", tree != null && tree.isObject());
                item.body().accept(tree);
            }
            byte[] response = json.writeValueAsBytes(item.response());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(item.status(), response.length);
            exchange.getResponseBody().write(response);
        } catch (Throwable failure) {
            // JSON/library exceptions can echo input. Persist only our fixed assertion
            // labels or a generic diagnostic; synthetic random credentials stay private.
            handlerFailure.compareAndSet(null, failure instanceof AssertionError
                    ? failure.getMessage() : "Loopback request processing failed");
            try {
                byte[] response = "{\"message\":\"synthetic QA handler failure\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception ignored) { }
        } finally {
            exchange.close();
        }
    }

    private void step(String name, String method, String path, int auth, int status,
                      Consumer<JsonNode> body, Map<String, Object> response, Runnable action) {
        int initial = assertions.get();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", name);
        record.put("method", method);
        record.put("path", path);
        record.put("expectedHttpStatus", status);
        try {
            check(name + ": no unfinished prior request", expected.get() == null);
            expected.set(new Expected(name, method, path, auth, status, body, response));
            action.run();
            check(name + ": request received", expected.get() == null);
            check(name + ": server assertions", handlerFailure.get() == null);
            record.put("status", "PASS");
        } catch (Throwable failure) {
            record.put("status", "FAIL");
            String serverError = handlerFailure.get();
            record.put("error", serverError != null ? serverError
                    : failure instanceof AssertionError ? failure.getMessage() : "Client operation failed");
            if (serverError != null) throw new AssertionError(serverError);
            throw failure;
        } finally {
            record.put("assertionCount", assertions.get() - initial);
            cases.add(record);
        }
    }

    private void heartbeatBody(JsonNode body) {
        check("heartbeat endpoint JSON", endpoint.equals(body.path("endpointId").asText()));
        check("heartbeat version JSON", body.path("currentPolicyVersion").asLong(-1) == 4);
    }

    private void ackBody(JsonNode body, long version) {
        check("ACK version JSON", body.path("appliedPolicyVersion").asLong(-1) == version);
        check("ACK rule count JSON", body.path("ruleCount").asInt(-1) == 1);
        check("ACK time JSON", sameInstant("ack.appliedAt", body.path("appliedAt")));
    }

    private boolean sameInstant(String field, JsonNode value) {
        instantWireTypes.put(field, value.getNodeType().name());
        // Server request fields are Instant (AgentPolicyController.Ack and
        // AgentEventRequest). Standard Jackson JavaTime supports ISO text and
        // numeric epoch seconds/fraction; assert the actual instant, not spelling.
        try { return occurredAt.equals(json.treeToValue(value, Instant.class)); }
        catch (Exception invalid) { return false; }
    }

    private void eventBody(JsonNode body) {
        check("event endpoint JSON", endpoint.equals(body.path("endpointId").asText()));
        check("event hostname JSON", "qa-http-transport".equals(body.path("hostname").asText()));
        check("event user JSON", "qa-local-user".equals(body.path("linuxUsername").asText()));
        check("event UID JSON", body.path("linuxUid").asLong(-1) == 1001);
        check("event session JSON", "qa-session".equals(body.path("sessionId").asText())
                && "wayland".equals(body.path("sessionType").asText()) && "seat0".equals(body.path("seat").asText()));
        check("event type/decision JSON", "CONNECTED".equals(body.path("eventType").asText())
                && "BLOCKED".equals(body.path("decision").asText()));
        check("event source time JSON", sameInstant("event.occurredAt", body.path("occurredAt")));
        check("event no-serial/hash JSON", "".equals(body.path("device").path("serial").asText("missing"))
                && "qaSyntheticHash=".equals(body.path("device").path("hash").asText()));
        check("event VID/PID/class JSON", "0951".equals(body.path("device").path("vendorId").asText())
                && "1666".equals(body.path("device").path("productId").asText())
                && "08:06:50".equals(body.path("device").path("interfaceClass").asText()));
    }

    private void policyResult(AdminServerClient client) {
        var result = client.fetchPolicy(endpoint, agentToken);
        check("policy parsed", result.isPresent());
        var value = result.orElseThrow();
        check("policy version parsed", value.getPolicyVersion() == 5L);
        check("canonical whitelist parsed", value.getWhitelistedDevices().size() == 1);
        var device = value.getWhitelistedDevices().getFirst();
        check("policy hash/no-serial parsed", "qaSyntheticHash=".equals(device.getDeviceHash()) && "".equals(device.getSerialNumber()));
    }

    private void exercise() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        var worker = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "qa-http-peer"); thread.setDaemon(true); return thread; });
        server.setExecutor(worker);
        server.createContext("/", this::handle);
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setEnrollmentToken(enrollmentToken);
            // This is the production constructor; no RestClient is injected/mocked.
            AdminServerClient client = new AdminServerClient(properties);
            var enrollment = EnrollmentRequest.builder().hostname("qa-http-transport").machineId(machine)
                    .ipAddress("127.0.0.1").enrollmentToken(enrollmentToken).build();
            var heartbeat = HeartbeatRequest.builder().endpointId(endpoint).currentPolicyVersion(4L).build();
            var ack = PolicyAckRequest.builder().appliedPolicyVersion(5L).appliedAt(occurredAt).ruleCount(1).build();
            var futureAck = PolicyAckRequest.builder().appliedPolicyVersion(6L).appliedAt(occurredAt).ruleCount(1).build();
            var event = UsbEventPayload.builder().endpointId(endpoint).hostname("qa-http-transport")
                    .linuxUsername("qa-local-user").linuxUid(1001L).sessionId("qa-session").sessionType("wayland").seat("seat0")
                    .eventType("CONNECTED").decision("BLOCKED").occurredAt(occurredAt)
                    .device(Map.of("vendorId", "0951", "productId", "1666", "serial", "", "hash", "qaSyntheticHash=", "interfaceClass", "08:06:50")).build();
            Map<String, Object> ok = Map.of("success", true);
            Map<String, Object> error = Map.of("message", "synthetic QA rejection");
            Map<String, Object> policy = Map.of("policyVersion", 5, "whitelistedDevices", List.of(Map.of(
                    "vendorId", "0951", "productId", "1666", "serialNumber", "", "deviceHash", "qaSyntheticHash=", "deviceName", "QA storage", "fingerprintValue", "qaSyntheticHash=")));

            step("enroll", "POST", "/api/agent/enroll", 0, 200, body -> {
                check("enrollment host JSON", "qa-http-transport".equals(body.path("hostname").asText()));
                check("enrollment machine JSON", machine.equals(body.path("machineId").asText()));
                check("enrollment address JSON", "127.0.0.1".equals(body.path("ipAddress").asText()));
                check("enrollment token JSON", enrollmentToken.equals(body.path("enrollmentToken").asText()));
            }, Map.of("endpointId", endpoint, "agentToken", agentToken, "currentPolicyVersion", 5), () -> {
                var result = client.enroll(enrollment);
                check("enrollment parsed", result.isPresent());
                check("enrollment identity/token/version parsed", endpoint.equals(result.orElseThrow().getEndpointId())
                        && agentToken.equals(result.orElseThrow().getAgentToken()) && result.orElseThrow().getCurrentPolicyVersion() == 5L);
            });
            step("heartbeat", "POST", "/api/agent/heartbeat", 1, 200, this::heartbeatBody,
                    Map.of("status", "ONLINE", "serverPolicyVersion", 5, "lastAppliedPolicyVersion", 4), () -> {
                var result = client.sendHeartbeat(heartbeat, agentToken);
                check("heartbeat parsed", result.isPresent());
                check("heartbeat status/versions parsed", "ONLINE".equals(result.orElseThrow().getStatus())
                        && result.orElseThrow().getServerPolicyVersion() == 5L && result.orElseThrow().getLastAppliedPolicyVersion() == 4L);
            });
            step("policy", "GET", "/api/agent/policy", 1, 200, body -> {}, policy, () -> policyResult(client));
            step("ack", "POST", "/api/agent/policy/ack", 1, 200, body -> ackBody(body, 5), ok,
                    () -> check("ACK success", client.acknowledgePolicy(endpoint, agentToken, ack)));
            step("event", "POST", "/api/agent/events", 1, 200, this::eventBody, ok,
                    () -> check("event success", client.sendEvent(endpoint, agentToken, event)));
            step("heartbeat-rejected-token", "POST", "/api/agent/heartbeat", 2, 401, this::heartbeatBody, error,
                    () -> check("rejected heartbeat empty", client.sendHeartbeat(heartbeat, rejectedToken).isEmpty()));
            step("policy-rejected-token", "GET", "/api/agent/policy", 2, 403, body -> {}, error,
                    () -> check("rejected policy empty", client.fetchPolicy(endpoint, rejectedToken).isEmpty()));
            step("ack-rejected-token", "POST", "/api/agent/policy/ack", 2, 401, body -> ackBody(body, 5), error,
                    () -> check("rejected ACK false", !client.acknowledgePolicy(endpoint, rejectedToken, ack)));
            step("event-rejected-token", "POST", "/api/agent/events", 2, 401, this::eventBody, error,
                    () -> check("rejected event false", !client.sendEvent(endpoint, rejectedToken, event)));
            step("future-ack", "POST", "/api/agent/policy/ack", 1, 409, body -> ackBody(body, 6), error,
                    () -> check("future ACK false", !client.acknowledgePolicy(endpoint, agentToken, futureAck)));
            step("policy-503", "GET", "/api/agent/policy", 1, 503, body -> {}, error,
                    () -> check("503 policy empty", client.fetchPolicy(endpoint, agentToken).isEmpty()));
            step("policy-recovery", "GET", "/api/agent/policy", 1, 200, body -> {}, policy, () -> policyResult(client));
            step("event-503", "POST", "/api/agent/events", 1, 503, this::eventBody, error,
                    () -> check("503 event false", !client.sendEvent(endpoint, agentToken, event)));
            step("event-recovery", "POST", "/api/agent/events", 1, 200, this::eventBody, ok,
                    () -> check("recovered event success", client.sendEvent(endpoint, agentToken, event)));
            server.stop(0);
            int beforeClosedPort = received.get();
            int initial = assertions.get();
            check("closed-port heartbeat empty", client.sendHeartbeat(heartbeat, agentToken).isEmpty());
            check("closed-port request not received", received.get() == beforeClosedPort);
            cases.add(Map.of("name", "connection-refused-after-stop", "status", "PASS", "assertionCount", assertions.get() - initial));
        } finally {
            server.stop(0);
            worker.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output summary path");
        var smoke = new AgentHttpTransportSmoke();
        var result = new LinkedHashMap<String, Object>();
        result.put("constructor", "AdminServerClient(AgentProperties)");
        result.put("scope", "Real loopback HTTP using production client and controlled JDK HttpServer; synthetic credentials/data. No real Admin Server/Spring Security, retry scheduler, USBGuard, USB, Ubuntu or GUI execution.");
        result.put("startedAt", Instant.now().toString());
        int exitCode = 0;
        try {
            smoke.exercise();
            smoke.check("all requested scenarios completed", smoke.cases.size() == 15);
            result.put("status", "PASS");
        } catch (Throwable failure) {
            exitCode = 1;
            result.put("status", "FAIL");
            result.put("error", failure instanceof AssertionError ? failure.getMessage() : "HTTP smoke could not complete");
        } finally {
            result.put("endedAt", Instant.now().toString());
            result.put("assertionCount", smoke.assertions.get());
            result.put("requestCount", smoke.received.get());
            result.put("scenarioCount", smoke.cases.size());
            result.put("scenarios", smoke.cases);
            result.put("instantWireTypes", smoke.instantWireTypes);
            if (smoke.handlerFailure.get() != null) result.put("serverAssertionFailure", smoke.handlerFailure.get());
            Files.writeString(Path.of(args[0]), smoke.json.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
        }
        System.out.println("HTTP_SMOKE " + result.get("status") + "; scenarios=" + smoke.cases.size() + "; assertions=" + smoke.assertions.get());
        if (exitCode != 0) System.exit(exitCode);
    }
}
