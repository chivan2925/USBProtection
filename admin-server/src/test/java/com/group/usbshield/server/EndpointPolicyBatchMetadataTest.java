package com.group.usbshield.server;

import com.group.usbshield.server.device.UsbDevice;
import com.group.usbshield.server.device.UsbDeviceRepository;
import com.group.usbshield.server.endpoint.Endpoint;
import com.group.usbshield.server.endpoint.EndpointRepository;
import com.group.usbshield.server.event.UsbEventRepository;
import com.group.usbshield.server.policy.EndpointPolicyService;
import com.group.usbshield.server.policy.EndpointWhitelistEntry;
import com.group.usbshield.server.policy.EndpointWhitelistEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EndpointPolicyBatchMetadataTest {
    @Test void listAndPolicyBatchLoadUsbMetadataInsteadOfNPlusOne() {
        var endpoints = mock(EndpointRepository.class);
        var entries = mock(EndpointWhitelistEntryRepository.class);
        var devices = mock(UsbDeviceRepository.class);
        var events = mock(UsbEventRepository.class);
        var service = new EndpointPolicyService(endpoints, entries, devices, events);
        when(endpoints.findByEndpointId("EP-A")).thenReturn(Optional.of(
                Endpoint.builder().endpointId("EP-A").hostname("lab-a").build()));
        when(endpoints.lockByEndpointId("EP-A")).thenReturn(Optional.of(
                Endpoint.builder().endpointId("EP-A").hostname("lab-a").build()));
        var first = EndpointWhitelistEntry.builder().id(11L).endpointId("EP-A").usbDeviceId(1L)
                .fingerprintType("HASH").fingerprintValue("hash-one").build();
        var second = EndpointWhitelistEntry.builder().id(12L).endpointId("EP-A").usbDeviceId(2L)
                .fingerprintType("HASH").fingerprintValue("hash-two").build();
        var third = EndpointWhitelistEntry.builder().id(13L).endpointId("EP-A").usbDeviceId(1L)
                .fingerprintType("HASH").fingerprintValue("hash-three").build();
        var legacy = EndpointWhitelistEntry.builder().id(14L).endpointId("EP-A")
                .fingerprintType("HASH").fingerprintValue("legacy-hash").build();
        when(entries.findByEndpointIdAndEnabledTrue("EP-A")).thenReturn(List.of(first, second, third, legacy));
        when(devices.findAllById(any())).thenReturn(List.of(
                UsbDevice.builder().id(1L).vendorId("0951").productId("1665").deviceName("Kingston").build(),
                UsbDevice.builder().id(2L).vendorId("1234").productId("5678").deviceName("Second").build()));

        var response = service.list("EP-A");
        assertEquals(java.util.Arrays.asList("Kingston", "Second", "Kingston", null),
                response.stream().map(EndpointPolicyService.EntryResponse::name).toList());
        assertEquals(4, service.policy("EP-A").getWhitelistedDevices().size());
        verify(devices, times(2)).findAllById(any()); // once for list, once for policy
        verify(devices, never()).findById(any());
    }
}
