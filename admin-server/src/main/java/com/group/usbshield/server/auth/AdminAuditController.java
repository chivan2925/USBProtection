package com.group.usbshield.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {
    private final JdbcTemplate jdbc;

    public record AuditRow(long id, String actorUsername, String action, String targetId, Instant occurredAt) {}
    public record AuditPage(List<AuditRow> content, int page, int size, long totalElements) {}

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AuditPage list(@AuthenticationPrincipal AdminIdentity admin,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit pagination");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM admin_audit WHERE workspace_id = ?", Long.class, admin.workspaceId());
        var rows = jdbc.query("""
                SELECT audit.id, actor.username, audit.action, audit.target_id, audit.occurred_at
                FROM admin_audit audit JOIN admin_users actor ON actor.id = audit.actor_admin_id
                WHERE audit.workspace_id = ?
                ORDER BY audit.occurred_at DESC, audit.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new AuditRow(rs.getLong("id"), rs.getString("username"),
                rs.getString("action"), rs.getString("target_id"), rs.getTimestamp("occurred_at").toInstant()),
                admin.workspaceId(), size, page * size);
        return new AuditPage(rows, page, size, total);
    }
}
