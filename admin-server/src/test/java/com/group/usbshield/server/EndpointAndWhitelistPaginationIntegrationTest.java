package com.group.usbshield.server;

import com.group.usbshield.server.endpoint.AdminEndpointController;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.policy.AdminWhitelistPageController;
import com.group.usbshield.server.policy.EndpointWhitelistEntry;
import com.group.usbshield.server.policy.EndpointWhitelistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@IsolatedPostgresDb
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Transactional
class EndpointAndWhitelistPaginationIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        TestPostgresSchemas.register(registry, EndpointAndWhitelistPaginationIntegrationTest.class);
    }

    @Autowired EndpointRepository endpoints;
    @Autowired EndpointWhitelistEntryRepository whitelist;
    @Autowired UsbDeviceRepository devices;
    @Autowired AdminEndpointController endpointController;
    @Autowired AdminWhitelistPageController whitelistController;

    @Test void endpointSearchFiltersBeforePagingAndCountsGlobalStatus() {
        String marker = "paging-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            endpoints.save(Endpoint.builder().endpointId("EP-" + marker + "-" + i)
                    .hostname(marker + "-node-" + i)
                    .lastSeen(i == 0 ? Instant.now() : null).build());
        }
        var first = endpointController.page(0, 2, marker, null);
        var second = endpointController.page(1, 2, marker, null);
        assertEquals(3, first.totalElements());
        assertEquals(2, first.content().size());
        assertEquals(1, second.content().size());
        assertTrue(first.onlineEndpoints() >= 1);
        assertTrue(first.offlineEndpoints() >= 2);
        assertEquals(1, endpointController.page(0, 20, marker, "online").totalElements());
        assertEquals(2, endpointController.page(0, 20, marker, "offline").totalElements());
        assertEquals(0, endpointController.page(0, 20, "a-nonmatching-term", null).totalElements());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> endpointController.page(-1, 20, null, null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> endpointController.page(0, 101, null, null));
    }

    @Test void whitelistSearchIsGloballyPagedAndIncludesDeviceMetadata() {
        String marker = "WL-" + UUID.randomUUID().toString().substring(0, 8);
        var usb = devices.save(UsbDevice.builder().vendorId("0951").productId("1665")
                .deviceHash("hash-" + UUID.randomUUID()).deviceName(marker).build());
        for (int i = 0; i < 3; i++) {
            whitelist.save(EndpointWhitelistEntry.builder().endpointId(marker + "-" + i)
                    .usbDeviceId(usb.getId()).fingerprintType("HASH")
                    .fingerprintValue("wl-" + i + "-" + marker).enabled(true).build());
        }
        devices.flush();
        whitelist.flush();
        var first = whitelistController.page(0, 2, null, marker);
        assertEquals(3, first.totalElements());
        assertEquals(2, first.content().size());
        assertTrue(first.content().stream().allMatch(e -> marker.equals(e.name())));
        assertEquals(1, whitelistController.page(1, 2, null, marker).content().size());
        assertEquals(1, whitelistController.page(0, 20, marker + "-1", marker).totalElements());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> whitelistController.page(0, 0, null, null));
    }
}
