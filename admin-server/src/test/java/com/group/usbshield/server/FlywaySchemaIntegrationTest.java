package com.group.usbshield.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** Applies V1+V2 in an empty isolated schema, then passes Hibernate validate. */
@IsolatedPostgresDb
@SpringBootTest
class FlywaySchemaIntegrationTest {
    @DynamicPropertySource
    static void isolatedPostgres(DynamicPropertyRegistry registry) {
        TestPostgresSchemas.register(registry, FlywaySchemaIntegrationTest.class, true);
    }

    @Autowired JdbcTemplate jdbc;
    @Test void freshSchemaMigratesAndValidatesWithoutWritingApplicationDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("usbshield_test");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema=current_schema() and table_name='endpoints' "
                + "and column_name in ('usbguard_reported_status','usbguard_checked_at')", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema=current_schema() and table_name='admin_users' "
                + "and column_name='credential_version'", Long.class)).isEqualTo(1L);
    }
}
