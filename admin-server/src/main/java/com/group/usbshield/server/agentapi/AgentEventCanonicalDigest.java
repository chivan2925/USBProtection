package com.group.usbshield.server.agentapi;

import com.group.usbshield.server.agentapi.dto.AgentEventRequest;
import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable, unambiguous hash of every audit-relevant field, including device metadata. */
final class AgentEventCanonicalDigest {
    private AgentEventCanonicalDigest() {}

    static String digest(String endpointId, AgentEventRequest event) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            field(hash, "usbshield-event-v1");
            field(hash, endpointId); // authenticated identity, not user-supplied endpoint
            field(hash, event.getHostname());
            String username = event.getLinuxUsername();
            field(hash, username);
            field(hash, "UNKNOWN".equals(username) ? null : stringify(event.getLinuxUid()));
            field(hash, "UNKNOWN".equals(username) ? null : event.getSessionId());
            field(hash, event.getSessionType());
            field(hash, event.getSeat());
            field(hash, event.getEventType());
            field(hash, event.getDecision());
            field(hash, event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
            AgentUsbDeviceDto device = event.getDevice();
            if (device == null) {
                field(hash, null);
            } else {
                field(hash, "device-present");
                field(hash, device.getVendorId());
                field(hash, device.getProductId());
                field(hash, device.getSerial());
                field(hash, device.getName());
                field(hash, device.getHash());
                field(hash, device.getInterfaceClass());
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String stringify(Object value) { return value == null ? null : value.toString(); }

    private static void field(MessageDigest hash, String value) {
        if (value == null) { hash.update((byte) 0); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hash.update((byte) 1);
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        hash.update(bytes);
    }
}
