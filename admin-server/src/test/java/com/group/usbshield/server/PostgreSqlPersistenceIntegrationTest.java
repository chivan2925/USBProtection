package com.group.usbshield.server;

import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.event.UsbEvent;
import com.group.usbshield.server.event.UsbEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Executes the real PostgreSQL schema, JDBC dashboard SQL and endpoint-scoped inventory. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IsolatedPostgresDb
@Transactional
class PostgreSqlPersistenceIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired UsbEventRepository events;
    @Autowired MockMvc mvc;

    @Test
    void newDatabaseUsesPostgresAndEnforcesUniqueNonNullHash() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'usb_devices'
                  AND indexdef ILIKE '%UNIQUE%' AND indexdef ILIKE '%device_hash%'
                """, Long.class)).isGreaterThanOrEqualTo(1L);
        String hash = "PG-" + UUID.randomUUID();
        var device = devices.saveAndFlush(UsbDevice.builder()
                .vendorId("0951").productId("1666").deviceHash(hash).build());
        assertThat(devices.findByDeviceHash(hash)).contains(device);
        // Missing identities remain independent in the catalog, not one shared empty string.
        var blank = devices.saveAndFlush(UsbDevice.builder()
                .vendorId("0951").productId("1666").deviceHash("\t").build());
        assertThat(blank.getDeviceHash()).isNull();
    }

    @Test
    void nativeDashboardQueriesAndEndpointFilteringRunOnPostgres() throws Exception {
        String endpointId = "PG-SMOKE-" + UUID.randomUUID();
        endpoints.saveAndFlush(Endpoint.builder().endpointId(endpointId).hostname("pg-test-host").build());
        var device = devices.saveAndFlush(UsbDevice.builder().vendorId("0951")
                .productId("1666").deviceName("PG Test USB")
                .deviceHash("PG-" + UUID.randomUUID()).build());
        events.saveAndFlush(UsbEvent.builder().endpointId(endpointId).usbDeviceId(device.getId())
                .eventType("CONNECTED").decision("BLOCKED")
                .occurredAt(Instant.parse("2026-11-03T01:30:00Z")).build());
        mvc.perform(get("/api/admin/dashboard/summary").with(user("qa").roles("ADMIN"))
                    .param("from", "2026-11-03T00:00:00Z")
                    .param("to", "2026-11-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDevices").value(1))
                .andExpect(jsonPath("$.blockedDevices").value(1))
                .andExpect(jsonPath("$.blockedToday").value(1));
        mvc.perform(get("/api/admin/device-states").with(user("qa").roles("ADMIN"))
                    .param("endpointId", endpointId).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("PG Test USB"));
    }
}
