package com.group.usbshield.server.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** One bounded query for global whitelist; avoids one HTTP/database lookup per Endpoint. */
@RestController
@RequestMapping("/api/admin/whitelist")
@RequiredArgsConstructor
public class AdminWhitelistPageController {
    private final NamedParameterJdbcTemplate jdbc;
    public record WhitelistPage(List<EndpointPolicyService.EntryResponse> content,
                                int page, int size, long totalElements, int totalPages) {}

    private static EndpointPolicyService.EntryResponse row(ResultSet rs, int index) throws SQLException {
        var stamp = rs.getTimestamp("created_at");
        return new EndpointPolicyService.EntryResponse(rs.getLong("id"), rs.getString("endpoint_id"),
                rs.getObject("usb_device_id", Long.class), rs.getString("fingerprint_type"),
                rs.getString("fingerprint_value"), true, rs.getString("device_name"),
                rs.getString("vendor_id"), rs.getString("product_id"),
                rs.getString("serial_number"), stamp == null ? null : stamp.toInstant());
    }

    @GetMapping("/page")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WhitelistPage page(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String endpointId,
                              @RequestParam(required = false) String q) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid whitelist pagination");
        if (q != null && q.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Whitelist search too long");
        if (endpointId != null && (endpointId.isBlank() || endpointId.length() > 100))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endpoint ID");

        var where = new StringBuilder(" WHERE w.enabled = TRUE");
        var values = new MapSqlParameterSource().addValue("size", size).addValue("offset", page * size);
        if (endpointId != null) {
            where.append(" AND w.endpoint_id = :endpointId");
            values.addValue("endpointId", endpointId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND LOWER(CONCAT(COALESCE(d.device_name, ''), ' ', w.endpoint_id, ' ', ")
                    .append("w.fingerprint_value, ' ', COALESCE(d.vendor_id, ''), ' ', ")
                    .append("COALESCE(d.product_id, ''), ' ', COALESCE(d.serial_number, ''))) ")
                    .append("LIKE :pattern ESCAPE '!'");
            values.addValue("pattern", "%" + q.strip().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        String tables = " FROM endpoint_whitelist_entries w LEFT JOIN usb_devices d ON d.id = w.usb_device_id";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + tables + where, values, Long.class);
        String select = "SELECT w.id, w.endpoint_id, w.usb_device_id, w.fingerprint_type, "
                + "w.fingerprint_value, w.created_at, d.device_name, d.vendor_id, "
                + "d.product_id, d.serial_number" + tables + where
                + " ORDER BY w.created_at DESC, w.id DESC LIMIT :size OFFSET :offset";
        var content = jdbc.query(select, values, AdminWhitelistPageController::row);
        return new WhitelistPage(content, page, size, total, (int) ((total + size - 1) / size));
    }
}
