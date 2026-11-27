package com.group.usbshield.server;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(TestPostgresSchemaCleanup.class)
class LegacyOwnershipMigrationTest {
    @Test void migrationRejectsUnmappedLegacyDataAndRollsBackWithoutModifyingIt() {
        var legacy = TestPostgresSchemas.flywayFor(getClass()).target("2").load();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(2);
        var db = new JdbcTemplate(legacy.getConfiguration().getDataSource());
        db.update("""
            insert into endpoints (endpoint_id, hostname, status, policy_version, applied_policy_version)
            values ('legacy-synthetic', 'before-cutover', 'OFFLINE', 1, 0)
            """);

        var upgrade = TestPostgresSchemas.flywayFor(getClass()).load();
        assertThrows(FlywayException.class, upgrade::migrate);
        assertThat(db.queryForObject("select hostname from endpoints where endpoint_id = 'legacy-synthetic'", String.class))
                .isEqualTo("before-cutover");
        assertThat(db.queryForObject("select count(*) from flyway_schema_history where success", Long.class))
                .isEqualTo(2L);
        assertThat(db.queryForObject("select to_regclass('workspaces')::text", String.class)).isNull();
    }
}
