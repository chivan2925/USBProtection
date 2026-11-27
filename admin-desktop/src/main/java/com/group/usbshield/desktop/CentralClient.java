package com.group.usbshield.desktop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.group.usbshield.desktop.CentralData.*;

/** Synchronous transport. The desktop calls it through a single background executor. */
final class CentralClient implements AutoCloseable {
    private final URI origin;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final HttpClient http;

    CentralClient(URI origin, SSLContext trust) {
        this.origin = CentralTls.origin(origin);
        var tls = new SSLParameters();
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        this.http = HttpClient.newBuilder().sslContext(trust).sslParameters(tls).cookieHandler(cookies)
                .proxy(ProxySelector.of(null)).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(4)).build();
    }

    Session login(String username, String password) throws IOException, InterruptedException {
        discardSession();
        try {
            mutate("POST", "/api/auth/login", Map.of("username", username, "password", password), Session.class);
            return get("/api/auth/me", Session.class);
        } catch (IOException | InterruptedException failure) {
            discardSession();
            throw failure;
        }
    }

    void logout() throws IOException, InterruptedException {
        try {
            mutate("POST", "/api/auth/logout", null, Void.class);
        } finally {
            discardSession();
        }
    }

    void changeCredentials(CredentialChange change) throws IOException, InterruptedException {
        mutate("POST", "/api/admin/account/credentials", change, Void.class);
        discardSession();
    }

    void discardSession() {
        cookies.getCookieStore().removeAll();
    }

    void checkReady() throws IOException, InterruptedException {
        csrf();
    }

    Dashboard dashboard(Instant from, Instant to) throws IOException, InterruptedException {
        var summary = get(query("/api/admin/dashboard/summary",
                Map.of("from", from.toString(), "to", to.toString())), Summary.class);
        var counts = get("/api/admin/endpoints/page?page=0&size=1", EndpointCounts.class);
        var recent = events(0, Map.of(), 5);
        return new Dashboard(summary, counts, recent.content());
    }

    Page<Endpoint> endpoints(int page, String search, String status) throws IOException, InterruptedException {
        return page("/api/admin/endpoints/page", page, Map.of("q", search, "status", status), Endpoint.class);
    }

    Endpoint endpoint(String id) throws IOException, InterruptedException {
        return get(endpointPath(id), Endpoint.class);
    }

    Page<Device> devices(int page, String search, String endpoint, String status) throws IOException, InterruptedException {
        return page("/api/admin/device-states", page,
                Map.of("q", search, "endpointId", endpoint, "status", status), Device.class);
    }

    Page<Rule> whitelist(int page, String search, String endpoint) throws IOException, InterruptedException {
        return page("/api/admin/whitelist/page", page, Map.of("q", search, "endpointId", endpoint), Rule.class);
    }

    Page<Event> events(int page, Map<String, String> filters) throws IOException, InterruptedException {
        return page("/api/admin/events", page, filters, Event.class);
    }

    private Page<Event> events(int page, Map<String, String> filters, int size) throws IOException, InterruptedException {
        return page("/api/admin/events", page, size, filters, Event.class);
    }

    Event event(long id) throws IOException, InterruptedException {
        return get("/api/admin/events/" + id, Event.class);
    }

    Page<Audit> audit(int page) throws IOException, InterruptedException {
        return page("/api/admin/audit", page, Map.of(), Audit.class);
    }

    void allow(String endpoint, long device) throws IOException, InterruptedException {
        mutate("POST", endpointPath(endpoint) + "/whitelist", Map.of("usbDeviceId", device), Void.class);
    }

    void revoke(String endpoint, long rule) throws IOException, InterruptedException {
        mutate("DELETE", endpointPath(endpoint) + "/whitelist/" + rule, null, Void.class);
    }

    Slot allocateSlot(UUID installation, String hostname) throws IOException, InterruptedException {
        return mutate("POST", "/api/admin/enrollment/slots",
                Map.of("installationId", installation.toString(), "hostname", hostname), Slot.class);
    }

    Ticket issueTicket(String endpoint) throws IOException, InterruptedException {
        return mutate("POST", enrollmentPath(endpoint) + "/ticket", null, Ticket.class);
    }

    void revokeCredential(String endpoint) throws IOException, InterruptedException {
        mutate("DELETE", enrollmentPath(endpoint) + "/credential", null, Void.class);
    }

    private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
        return send("GET", path, null, json.constructType(type), null);
    }

    private <T> Page<T> page(String path, int index, Map<String, String> filters, Class<T> item)
            throws IOException, InterruptedException {
        return page(path, index, 20, filters, item);
    }

    private <T> Page<T> page(String path, int index, int size, Map<String, String> filters, Class<T> item)
            throws IOException, InterruptedException {
        var parameters = new LinkedHashMap<>(filters);
        parameters.put("page", Integer.toString(index));
        parameters.put("size", Integer.toString(size));
        var type = json.getTypeFactory().constructParametricType(Page.class, item);
        return send("GET", query(path, parameters), null, type, null);
    }

    private Csrf csrf() throws IOException, InterruptedException {
        var csrf = get("/api/auth/csrf", Csrf.class);
        if (!"X-CSRF-TOKEN".equalsIgnoreCase(csrf.headerName()) || csrf.token() == null || csrf.token().isBlank()) {
            throw new IOException("Unexpected CSRF contract");
        }
        return csrf;
    }

    private <T> T mutate(String method, String path, Object body, Class<T> type) throws IOException, InterruptedException {
        return send(method, path, body, json.constructType(type), csrf());
    }

    private <T> T send(String method, String path, Object body, JavaType type, Csrf csrf)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json");
        if (csrf != null) builder.header(csrf.headerName(), csrf.token());
        var content = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            content = HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body));
        }
        var response = http.send(builder.method(method, content).build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 401) discardSession();
        if (status < 200 || status >= 300) throw new RequestFailure(status);
        if (type.hasRawClass(Void.class)) return null;
        return json.readValue(response.body(), type);
    }

    private static String endpointPath(String endpoint) { return "/api/admin/endpoints/" + encode(endpoint); }
    private static String enrollmentPath(String endpoint) { return "/api/admin/enrollment/" + encode(endpoint); }

    private static String query(String path, Map<String, String> values) {
        var result = new StringBuilder(path);
        values.forEach((name, value) -> {
            if (!value.isBlank()) {
                result.append(result.length() == path.length() ? '?' : '&');
                result.append(encode(name)).append('=').append(encode(value.strip()));
            }
        });
        return result.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override public void close() { http.shutdownNow(); }

    private record Csrf(String headerName, String token) {}

    static final class RequestFailure extends IOException {
        private final int status;

        RequestFailure(int status) {
            super("HTTP " + status);
            this.status = status;
        }

        int status() { return status; }
    }
}
