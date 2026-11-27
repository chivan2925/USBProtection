package com.group.usbshield.server.device;

import com.group.usbshield.server.agentapi.dto.AgentUsbDeviceDto;
import com.group.usbshield.server.policy.UsbHashIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsbCatalogWriter {
    private final JdbcTemplate jdbc;

    public long observe(long workspaceId, AgentUsbDeviceDto device) {
        String hash = UsbHashIdentity.valid(device.getHash()) ? device.getHash() : null;
        if (hash != null) {
            var existing = jdbc.queryForList("SELECT id FROM usb_devices WHERE workspace_id = ? AND device_hash = ?",
                    Long.class, workspaceId, hash);
            if (!existing.isEmpty()) return existing.getFirst();
        }
        var inserted = jdbc.queryForList("""
                INSERT INTO usb_devices (workspace_id, device_hash, vendor_id, product_id, serial_number,
                                         device_name, device_interfaces, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (workspace_id, device_hash) DO NOTHING RETURNING id
                """, Long.class, workspaceId, hash, fallback(device.getVendorId()), fallback(device.getProductId()),
                device.getSerial(), device.getName(), device.getInterfaceClass());
        if (!inserted.isEmpty()) return inserted.getFirst();
        return jdbc.queryForObject("SELECT id FROM usb_devices WHERE workspace_id = ? AND device_hash = ?",
                Long.class, workspaceId, hash);
    }

    private static String fallback(String id) { return id == null ? "UNKNOWN" : id; }
}
