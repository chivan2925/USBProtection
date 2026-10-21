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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:event-name-search;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class EventSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EndpointRepository endpoints;
    @Autowired UsbDeviceRepository devices;
    @Autowired UsbEventRepository events;

    private Endpoint endpoint(String id, String hostname) {
        return endpoints.save(Endpoint.builder().endpointId(id).hostname(hostname).build());
    }

    private UsbDevice device(String name) {
        return devices.save(UsbDevice.builder().vendorId("0951").productId("1666").deviceName(name).build());
    }

    private UsbEvent event(String endpointId, String hostname, Long deviceId, String username, int second) {
        return events.save(UsbEvent.builder().endpointId(endpointId).hostname(hostname).usbDeviceId(deviceId)
            .linuxUsername(username).eventType("CONNECTED").decision("BLOCKED")
            .occurredAt(Instant.parse("2026-10-21T10:00:00Z").plusSeconds(second)).build());
    }

    @Test void endpointSearchMatchesVisibleSnapshotNameAndLegacyFallback() throws Exception {
        endpoint("EP-A", "CURRENT-LAB");
        endpoint("EP-B", "OTHER-HOST");
        event("EP-A", "DESKTOP-8STBDDD", null, "student01", 1);
        event("EP-A", null, null, "student01", 2);
        event("EP-B", "OTHER-HOST", null, "student02", 3);

        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("endpoint", " desktop-8st "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].hostname").value("DESKTOP-8STBDDD"));
        // A renamed endpoint only supplies its current hostname for legacy rows without a snapshot.
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("endpoint", "current-lab"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].hostname").value("CURRENT-LAB"));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("endpoint", "EP-A"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void deviceNameSearchFiltersBeforePaginationAndRetainsExactIdQueries() throws Exception {
        var kingston = device("Kingston DataTraveler 3.0");
        var sandisk = device("SanDisk Ultra");
        event("EP-B", "OTHER", sandisk.getId(), "other-user", 100);
        for (int second = 0; second < 25; second++) {
            event("EP-A", "DESKTOP-8STBDDD", kingston.getId(), "student01", second);
        }
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("device", " kInGsToN ").param("page", "1").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(5))
            .andExpect(jsonPath("$.content[0].device.name").value("Kingston DataTraveler 3.0"));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("device", kingston.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("endpointId", "EP-A").param("deviceId", kingston.getId().toString()).param("linuxUsername", "student01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test void nameFiltersCombineWithExactLinuxUsernameAndExistingFilters() throws Exception {
        var usb = device("Kingston DataTraveler 3.0");
        event("EP-A", "DESKTOP-8STBDDD", usb.getId(), "student01", 1);
        event("EP-A", "DESKTOP-8STBDDD", usb.getId(), "UNKNOWN", 2);
        event("EP-B", "OTHER-HOST", usb.getId(), "student01", 3);

        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("endpoint", "desktop").param("device", "datatraveler").param("linuxUsername", "student01")
                .param("decision", "BLOCKED").param("eventType", "CONNECTED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].endpointId").value("EP-A"));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("endpoint", "desktop").param("linuxUsername", "student"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("endpointId", "EP-B").param("endpoint", "desktop"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void numericSearchRetainsHistoricalRowsWithoutDeviceDetails() throws Exception {
        event("EP-A", "DESKTOP-8STBDDD", 999999L, "student01", 1);
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("device", "999999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].usbDeviceId").value(999999))
            .andExpect(jsonPath("$.content[0].device").isEmpty());
    }

    @Test void usernameSubstringSearchIsCaseInsensitiveAndPaged() throws Exception {
        event("EP-B", "OTHER", null, "administrator", 100);
        event("EP-B", "OTHER", null, "UNKNOWN", 101);
        for (int second = 0; second < 25; second++) {
            event("EP-A", "DESKTOP", null, second % 2 == 0 ? "student01" : "student02", second);
        }
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("username", " StUdEnT0 ").param("page", "1").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(5));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("username", "student").param("linuxUsername", "student01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN"))
                .param("username", "student").param("endpointId", "EP-B"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("username", "KnOw"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].linuxUsername").value("UNKNOWN"));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("username", " "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(27));
    }

    @Test void usernameSearchTreatsWildcardCharactersLiterally() throws Exception {
        event("EP-A", "DESKTOP", null, "user%_\\backup", 1);
        event("EP-B", "OTHER", null, "userXYzbackup", 2);
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("username", "%_\\"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].endpointId").value("EP-A"));
    }

    @Test void wildcardCharactersAreLiteralAndBlankQueriesDoNotFilter() throws Exception {
        var literal = device("USB 100%_safe\\backup");
        var other = device("USB 100xYsafeZbackup");
        event("EP-A", "LAB 100%_safe\\backup", literal.getId(), "UNKNOWN", 1);
        event("EP-B", "LAB 100xYsafeZbackup", other.getId(), "UNKNOWN", 2);

        for (String field : new String[]{"endpoint", "device"}) {
            mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param(field, "%_safe\\"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].endpointId").value("EP-A"));
        }
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("endpoint", " ").param("device", " "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/admin/events").with(user("qa").roles("ADMIN")).param("device", "9223372036854775808"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
