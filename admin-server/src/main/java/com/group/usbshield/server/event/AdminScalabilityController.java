package com.group.usbshield.server.event;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-side projections for dashboard and device inventory. This deliberately
 * does NOT download audit history and reconstruct it in the browser.
 *
 * A device identity is endpoint-scoped: HASH, else VID:PID:serial, else the
 * individual event for an unidentifiable USB. The latest observed event wins.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminScalabilityController {
    private final NamedParameterJdbcTemplate jdbc;
    private final UsbEventRepository events;

    private static final String IDENTITY = """
        CASE WHEN d.device_hash IS NOT NULL AND TRIM(d.device_hash) <> ''
                 THEN CONCAT('hash:', d.device_hash)
             WHEN d.serial_number IS NOT NULL AND TRIM(d.serial_number) <> ''
                 THEN CONCAT('serial:', d.vendor_id, ':', d.product_id, ':', d.serial_number)
             ELSE CONCAT('event:', CAST(e.id AS VARCHAR)) END
        """;

    // Columns use Hibernate's canonical snake_case naming strategy in PostgreSQL.
    // ROW_NUMBER chooses the latest event within a device lifecycle identity.
    private static final String LATEST = """
        WITH ranked AS (
          SELECT e.id AS event_id, e.endpoint_id, e.usb_device_id, e.event_type,
                 e.decision, e.occurred_at, COALESCE(e.hostname, ep.hostname) AS endpoint_name,
                 d.device_name, d.device_hash, d.vendor_id, d.product_id, d.serial_number,
                 """ + IDENTITY + """
                 AS identity_key,
                 ROW_NUMBER() OVER (PARTITION BY e.endpoint_id, """ + IDENTITY + """
                   ORDER BY e.occurred_at DESC, e.id DESC) AS rn
          FROM usb_events e
          JOIN usb_devices d ON d.id = e.usb_device_id
          LEFT JOIN endpoints ep ON ep.endpoint_id = e.endpoint_id
        ), current_devices AS (
          SELECT *, CASE WHEN event_type = 'DISCONNECTED' THEN 'disconnected'
                         WHEN decision = 'BLOCKED' THEN 'blocked'
                         WHEN decision = 'ALLOWED' THEN 'allowed'
                         ELSE 'detected' END AS status
          FROM ranked WHERE rn = 1
        )
        """;

    public record DeviceRow(String id, Long usbDeviceId, String hash, String name, String endpointId,
                            String endpointName, String vendorId, String productId, String serialNumber,
                            String status, Instant detectedAt, Instant blockedAt, String blockReason) {}
    public record DevicePage(List<DeviceRow> content, int page, int size, long totalElements, int totalPages) {}
    public record Summary(long totalDevices, long blockedDevices, long allowedDevices,
                          long recentEvents, long blockedToday) {}

    private static MapSqlParameterSource filters(String endpointId, String status, String search) {
        if (status != null && !Set.of("allowed", "blocked", "detected", "disconnected").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid device status");
        if (endpointId != null && endpointId.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endpoint ID");
        if (search != null && search.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search too long");
        String pattern = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new MapSqlParameterSource()
            .addValue("endpointId", endpointId)
            .addValue("status", status)
            .addValue("search", pattern);
    }

    private static String where(String endpointId, String status, Object searchPattern) {
        // Add only active filters so PostgreSQL never needs to infer types for NULL predicates.
        StringBuilder where = new StringBuilder("WHERE rn = 1");
        if (endpointId != null) where.append(" AND endpoint_id = :endpointId");
        if (status != null) where.append(" AND status = :status");
        if (searchPattern != null) where.append(" ").append("""
            AND LOWER(CONCAT(COALESCE(device_name, ''), ' ', vendor_id, ' ', product_id, ' ',
                COALESCE(serial_number, ''), ' ', endpoint_id, ' ',
                COALESCE(endpoint_name, ''), ' ', COALESCE(device_hash, '')))
                LIKE :search ESCAPE '!'
            """);
        return where.toString();
    }

    private static DeviceRow deviceRow(ResultSet rs, int unused) throws SQLException {
        String decision = rs.getString("decision");
        String status = rs.getString("status");
        Instant time = rs.getTimestamp("occurred_at").toInstant();
        return new DeviceRow(
            rs.getString("endpoint_id") + ":" + rs.getString("identity_key"),
            rs.getLong("usb_device_id"), rs.getString("device_hash"),
            rs.getString("device_name") == null ? "USB chưa có tên" : rs.getString("device_name"),
            rs.getString("endpoint_id"), rs.getString("endpoint_name"),
            rs.getString("vendor_id"), rs.getString("product_id"), rs.getString("serial_number"),
            status, time, "BLOCKED".equals(decision) ? time : null,
            "BLOCKED".equals(decision) ? "Quyết định BLOCKED trong sự kiện gần nhất." : null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @GetMapping("/device-states")
    public DevicePage devices(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String endpointId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String q) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        MapSqlParameterSource args = filters(endpointId, status, q);
        String where = where(endpointId, status, args.getValue("search"));
        long total = jdbc.queryForObject(LATEST + "SELECT COUNT(*) FROM current_devices " + where, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<DeviceRow> rows = jdbc.query(LATEST + """
            SELECT endpoint_id, identity_key, usb_device_id, device_hash, device_name, endpoint_name,
                   vendor_id, product_id, serial_number, status, decision, occurred_at
            FROM current_devices
            """ + where + " ORDER BY occurred_at DESC, event_id DESC LIMIT :limit OFFSET :offset", args,
            AdminScalabilityController::deviceRow);
        long pages = (total + size - 1) / size;
        if (pages > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Too many pages");
        return new DevicePage(rows, page, size, total, (int) pages);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @GetMapping("/dashboard/summary")
    public Summary summary(@RequestParam Instant from, @RequestParam Instant to) {
        if (!from.isBefore(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid day range");
        MapSqlParameterSource args = new MapSqlParameterSource()
            .addValue("from", from.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", to.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        // PostgreSQL preserves lower-case unquoted aliases; do not depend on
        // H2's upper-case Map keys. Read via ResultSet column labels instead.
        long[] totals = jdbc.query(LATEST + """
            SELECT COUNT(*) AS total_devices,
                   COALESCE(SUM(CASE WHEN status = 'blocked' THEN 1 ELSE 0 END), 0) AS blocked_devices,
                   COALESCE(SUM(CASE WHEN status = 'allowed' THEN 1 ELSE 0 END), 0) AS allowed_devices,
                   COALESCE(SUM(CASE WHEN status = 'blocked' AND occurred_at >= :from AND occurred_at < :to
                         THEN 1 ELSE 0 END), 0) AS blocked_today
            FROM current_devices
            """, args, rs -> {
                if (!rs.next()) throw new IllegalStateException("Dashboard aggregate returned no row");
                return new long[] { rs.getLong("total_devices"), rs.getLong("blocked_devices"),
                    rs.getLong("allowed_devices"), rs.getLong("blocked_today") };
            });
        return new Summary(totals[0], totals[1], totals[2], events.count(), totals[3]);
    }
}
