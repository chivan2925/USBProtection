package com.group.usbshield.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceAudit {
    private final JdbcTemplate jdbc;

    public void record(AdminIdentity identity, String action, String target) {
        record(identity.workspaceId(), identity.adminUserId(), action, target);
    }

    public void record(long workspaceId, long ownerId, String action, String target) {
        jdbc.update("""
            INSERT INTO admin_audit (workspace_id, actor_admin_id, action, target_id, occurred_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, workspaceId, ownerId, action, target);
    }
}
