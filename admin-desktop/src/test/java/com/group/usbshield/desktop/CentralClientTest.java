package com.group.usbshield.desktop;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.group.usbshield.desktop.LocalHttpsFixture.respond;
import static org.junit.jupiter.api.Assertions.*;

class CentralClientTest {
    @TempDir Path directory;

    @Test void desktopUsesSessionRotationCsrfScopedRoutesAndPaginationOverHttps() throws Exception {
        try (var fixture = new LocalHttpsFixture(directory, "ip:127.0.0.1")) {
            var requests = new CopyOnWriteArrayList<String>();
            fixture.server.createContext("/", exchange -> admin(exchange, requests));
            fixture.server.start();
            try (var client = new CentralClient(fixture.origin(), CentralTls.trust(fixture.ca))) {
                assertEquals("test-admin", client.login("test-admin", "fixture-password").username());
                var dashboard = client.dashboard(Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
                assertEquals(1, dashboard.summary().totalDevices());
                assertEquals(3, dashboard.endpoints().onlineEndpoints());
                assertEquals(1, dashboard.recent().size());
                var endpoints = client.endpoints(0, "cáp & USB", "online");
                assertTrue(endpoints.hasNext());
                assertEquals("EP-TEST", endpoints.content().getFirst().endpointId());
                assertEquals("EP-TEST", client.endpoint("EP-TEST").endpointId());
                assertEquals(9, client.devices(0, "", "EP-TEST", "").content().getFirst().usbDeviceId());
                assertEquals(12, client.whitelist(0, "", "EP-TEST").content().getFirst().id());
                assertEquals("usb-user", client.events(0, Map.of("username", "usb-user")).content().getFirst().linuxUsername());
                assertEquals("BLOCKED", client.event(4).decision());
                assertEquals("test-admin", client.audit(0).content().getFirst().actorUsername());
                client.allow("EP-TEST", 9);
                client.revoke("EP-TEST", 12);
                assertEquals("EP-TEST", client.allocateSlot(UUID.randomUUID(), "Fixture").endpointId());
                assertEquals("synthetic-ticket", client.issueTicket("EP-TEST").enrollmentToken());
                client.revokeCredential("EP-TEST");
                client.logout();
                assertEquals(401, assertThrows(CentralClient.RequestFailure.class,
                        () -> client.endpoint("EP-TEST")).status());
            }
            assertTrue(requests.stream().anyMatch(r -> r.contains("q=c%C3%A1p%20%26%20USB") && r.contains("size=20")));
            assertTrue(requests.stream().anyMatch(r -> r.contains("endpointId=EP-TEST")));
            assertTrue(requests.stream().anyMatch(r -> r.contains("username=usb-user")));
            assertEquals(7, requests.stream().filter(r -> r.equals("GET /api/auth/csrf")).count());
        }
    }

    @Test void rejectsUnknownCaAndIncorrectIpSan() throws Exception {
        try (var wrongSan = new LocalHttpsFixture(directory.resolve("wrong"), "dns:localhost");
             var otherCa = new LocalHttpsFixture(directory.resolve("other"), "ip:127.0.0.1")) {
            wrongSan.server.createContext("/", exchange -> respond(exchange, 200, "{}"));
            otherCa.server.createContext("/", exchange -> respond(exchange, 200, "{}"));
            wrongSan.server.start();
            otherCa.server.start();
            try (var client = new CentralClient(wrongSan.origin(), CentralTls.trust(wrongSan.ca));
                 var untrusted = new CentralClient(otherCa.origin(), CentralTls.trust(wrongSan.ca))) {
                assertThrows(SSLException.class, () -> client.endpoint("EP-TEST"));
                assertThrows(SSLException.class, () -> untrusted.endpoint("EP-TEST"));
            }
        }
    }

    @Test void loopbackOnlyAndNoRedirectsEvenToAnotherLocalPath() throws Exception {
        for (String value : List.of("http://127.0.0.1", "https://localhost", "https://192.168.1.1",
                "https://127.0.0.1@host.example", "https://127.0.0.1/elsewhere", "https://127.0.0.1?q=1")) {
            assertThrows(IllegalArgumentException.class, () -> CentralTls.origin(URI.create(value)));
        }
        try (var fixture = new LocalHttpsFixture(directory, "ip:127.0.0.1")) {
            fixture.server.createContext("/", exchange -> {
                exchange.getResponseHeaders().set("Location", "/different");
                respond(exchange, 302, "{}");
            });
            fixture.server.start();
            try (var client = new CentralClient(fixture.origin(), CentralTls.trust(fixture.ca))) {
                assertEquals(302, assertThrows(CentralClient.RequestFailure.class,
                        () -> client.endpoint("EP-TEST")).status());
            }
        }
    }

    @Test void inclusiveEventDateFallsBeforeNextLocalMidnightAtDatabasePrecision() {
        var zone = ZoneId.of("America/New_York");
        var date = LocalDate.parse("2025-03-09");
        var end = DesktopController.inclusiveEventEnd(date, zone);
        assertEquals(Instant.parse("2025-03-10T03:59:59.999999Z"), end);
        assertEquals(date, end.atZone(zone).toLocalDate());
        assertEquals(date.plusDays(1), end.plusNanos(1_000).atZone(zone).toLocalDate());
    }

    @Test void policyConfirmationsDistinguishSameNameUsbAndIdentifyExactRule() {
        var first = new CentralData.Device(11L, "hash-1", "Same model", "EP-TEST", null, null, null, null, null, null);
        var second = new CentralData.Device(22L, "hash-2", "Same model", "EP-TEST", null, null, null, null, null, null);
        assertTrue(DesktopController.deviceIdentity(first).contains("USB ID: 11\nHASH: hash-1"));
        assertTrue(DesktopController.deviceIdentity(second).contains("USB ID: 22\nHASH: hash-2"));
        var rule = new CentralData.Rule(33L, "EP-TEST", "hash-1", "Same model", null, null, null, null);
        assertTrue(DesktopController.ruleIdentity(rule).contains("Rule ID: 33\nHASH: hash-1"));
    }

    static void admin(HttpExchange exchange, List<String> requests) throws IOException {
        var uri = exchange.getRequestURI();
        String path = uri.getPath();
        String method = exchange.getRequestMethod();
        requests.add(method + " " + uri);
        String cookie = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", List.of()));
        boolean signedIn = cookie.contains("JSESSIONID=authenticated");
        String proof = signedIn ? "after-login" : "before-login";
        if (path.equals("/api/auth/csrf")) {
            if (!signedIn) exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=anonymous; Path=/; Secure; HttpOnly");
            respond(exchange, 200, "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"" + proof + "\"}");
            return;
        }
        if (!method.equals("GET") && !proof.equals(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"))) {
            respond(exchange, 403, "{}");
            return;
        }
        if (path.equals("/api/auth/login") && cookie.contains("JSESSIONID=anonymous")) {
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=authenticated; Path=/; Secure; HttpOnly");
            respond(exchange, 200, "{\"username\":\"test-admin\"}");
            return;
        }
        if (!signedIn) {
            respond(exchange, 401, "{}");
            return;
        }

        String endpoint = "{\"endpointId\":\"EP-TEST\",\"hostname\":\"Máy thử\",\"status\":\"ONLINE\"}";
        String event = "{\"id\":4,\"endpointId\":\"EP-TEST\",\"linuxUsername\":\"usb-user\",\"decision\":\"BLOCKED\"}";
        switch (path) {
            case "/api/auth/me" -> respond(exchange, 200, "{\"username\":\"test-admin\"}");
            case "/api/auth/logout" -> respond(exchange, 200, "{\"success\":true}");
            case "/api/admin/dashboard/summary" -> respond(exchange, 200, "{\"totalDevices\":1}");
            case "/api/admin/endpoints/page" -> {
                var result = page(endpoint, 21).replaceFirst("\\}$", ",\"onlineEndpoints\":3,\"offlineEndpoints\":1}");
                if (uri.getRawQuery().contains("page=1")) result = result.replace("\"page\":0", "\"page\":1");
                respond(exchange, 200, result);
            }
            case "/api/admin/endpoints/EP-TEST" -> respond(exchange, 200, endpoint);
            case "/api/admin/device-states" -> respond(exchange, 200,
                    page("{\"usbDeviceId\":9,\"hash\":\"hash-synthetic\",\"endpointId\":\"EP-TEST\"}", 1));
            case "/api/admin/whitelist/page" -> respond(exchange, 200, page("{\"id\":12}", 1));
            case "/api/admin/events" -> respond(exchange, 200, page(event, 1));
            case "/api/admin/events/4" -> respond(exchange, 200, event);
            case "/api/admin/audit" -> respond(exchange, 200, page("{\"actorUsername\":\"test-admin\"}", 1));
            case "/api/admin/endpoints/EP-TEST/whitelist" -> respond(exchange, method.equals("POST") ? 200 : 404, "{}");
            case "/api/admin/endpoints/EP-TEST/whitelist/12", "/api/admin/enrollment/EP-TEST/credential" ->
                    respond(exchange, method.equals("DELETE") ? 204 : 404, "");
            case "/api/admin/enrollment/slots" -> respond(exchange, 201, "{\"endpointId\":\"EP-TEST\"}");
            case "/api/admin/enrollment/EP-TEST/ticket" -> respond(exchange, 201, "{\"enrollmentToken\":\"synthetic-ticket\"}");
            default -> respond(exchange, 404, "{}");
        }
    }

    private static String page(String entry, int total) {
        return "{\"content\":[" + entry + "],\"page\":0,\"size\":20,\"totalElements\":" + total + "}";
    }
}
