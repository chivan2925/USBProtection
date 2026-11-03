package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:r02-catalog;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class ConcurrentUsbCatalogIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired PasswordEncoder encoder;

    @Test void twoIndependentEndpointsShareOneCanonicalUsbHash() throws Exception {
        String token = UUID.randomUUID().toString();
        String a = "EP-R02-A-" + UUID.randomUUID();
        String b = "EP-R02-B-" + UUID.randomUUID();
        endpoints.save(Endpoint.builder().endpointId(a).hostname("ubuntu-a")
                .agentTokenHash(encoder.encode(token)).build());
        endpoints.save(Endpoint.builder().endpointId(b).hostname("ubuntu-b")
                .agentTokenHash(encoder.encode(token)).build());
        String hash = "R02-same-hash-" + UUID.randomUUID();
        CountDownLatch gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> post(gate, a, token, hash));
            var second = pool.submit(() -> post(gate, b, token, hash));
            gate.countDown();
            assertEquals(HttpStatus.OK, first.get(20, TimeUnit.SECONDS));
            assertEquals(HttpStatus.OK, second.get(20, TimeUnit.SECONDS));
            assertEquals(1L, devices.findAll().stream()
                    .filter(device -> hash.equals(device.getDeviceHash())).count());
        } finally { pool.shutdownNow(); }
    }

    private HttpStatusCode post(CountDownLatch gate, String id, String token, String hash) {
        try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Endpoint-Id", id);
        headers.set("X-Agent-Token", token);
        Map<String, Object> body = Map.of(
                "endpointId", id, "hostname", "ubuntu-test", "linuxUsername", "UNKNOWN",
                "eventId", UUID.randomUUID().toString(), "eventType", "CONNECTED",
                "decision", "BLOCKED", "occurredAt", "2026-11-03T01:53:03Z",
                "device", Map.of("vendorId", "0951", "productId", "1665", "hash", hash,
                        "name", "UsbDevice", "interfaceClass", "08:06:50"));
        return http.postForEntity("/api/agent/events", new HttpEntity<>(body, headers), String.class).getStatusCode();
    }
}
