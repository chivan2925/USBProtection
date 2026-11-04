package com.group.usbshield.server;

import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.event.UsbEvent;
import com.group.usbshield.server.event.UsbEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@Transactional
class AdminScalabilityIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        com.group.usbshield.server.TestPostgresSchemas.register(registry, AdminScalabilityIntegrationTest.class);
    }

    @Autowired MockMvc mvc;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired UsbEventRepository events;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;

    private UsbDevice device(String name) {
        return devices.save(UsbDevice.builder().vendorId("0951").productId("1666")
            .deviceName(name).deviceHash("P3-" + UUID.randomUUID()).build());
    }

    @Test void r09EventPageUsesBatchMetadataQueriesNotNPlusOne() throws Exception {
        endpoints.save(Endpoint.builder().endpointId("P3-BATCH").hostname("lab-batch").build());
        Instant t = Instant.parse("2026-11-03T01:00:00Z");
        for (int i = 0; i < 100; i++) {
            var d = device("USB-" + i);
            events.save(UsbEvent.builder().endpointId("P3-BATCH").hostname(null)
                .usbDeviceId(d.getId()).eventType("CONNECTED").decision("BLOCKED")
                .occurredAt(t.plusSeconds(i)).build());
        }
        em.flush();
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("page", "0").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(100))
            .andExpect(jsonPath("$.content[0].device.name").isNotEmpty())
            .andExpect(jsonPath("$.content[0].hostname").value("lab-batch"));
        assertTrue(stats.getPrepareStatementCount() <= 5,
            "100 events must not produce 100 separate USB/hostname SQL queries: " + stats.getPrepareStatementCount());
    }

    @Test void r10SummaryAndDevicePagingStillWorkAfterMoreThanTenThousandEvents() throws Exception {
        endpoints.saveAndFlush(Endpoint.builder().endpointId("P3-LARGE").hostname("lab-large").build());
        var d = device("USB-LARGE");
        em.flush();
        Instant t = Instant.parse("2026-11-03T01:00:00Z");
        int total = 10050;
        // JDBC batching keeps the regression test practical on Windows CI.
        for (int start = 0; start < total; start += 250) {
            final int from = start;
            final int count = Math.min(250, total - start);
            jdbc.batchUpdate("""
                INSERT INTO usb_events (endpoint_id, hostname, usb_device_id, linux_username,
                   event_type, decision, occurred_at, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    Instant time = t.plusSeconds(from + i);
                    ps.setString(1, "P3-LARGE"); ps.setString(2, "lab-large");
                    ps.setLong(3, d.getId()); ps.setString(4, "UNKNOWN");
                    ps.setString(5, "CONNECTED"); ps.setString(6, "BLOCKED");
                    ps.setTimestamp(7, Timestamp.from(time)); ps.setTimestamp(8, Timestamp.from(time));
                }
                @Override public int getBatchSize() { return count; }
            });
        }
        mvc.perform(get("/api/admin/dashboard/summary").with(user("qa").roles("ADMIN"))
                .param("from", "2026-11-03T00:00:00Z").param("to", "2026-11-04T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentEvents").value(total))
            .andExpect(jsonPath("$.totalDevices").value(1))
            .andExpect(jsonPath("$.blockedDevices").value(1));
        mvc.perform(get("/api/admin/device-states").with(user("qa").roles("ADMIN"))
                .param("page", "0").param("size", "20").param("endpointId", "P3-LARGE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("page", "0").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(total))
            .andExpect(jsonPath("$.content.length()").value(20));
    }
}
