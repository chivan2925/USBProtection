package com.group.usbshield.server;

import org.springframework.test.context.DynamicPropertyRegistry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fail-closed test DB provisioning. No test suite runs DDL in application DB.
 * PostgreSQL CREATE on usbshield_test is required for isolated per-class schemas.
 */
public final class TestPostgresSchemas {
    private static final Map<Class<?>, Schema> schemas = new ConcurrentHashMap<>();
    private static final String DATABASE = "usbshield_test";
    private static final String ROLE = "usbshield_test";
    private record Schema(String url, String adminUrl, String password, String name) {}

    private TestPostgresSchemas() {}

    public static void register(DynamicPropertyRegistry registry, Class<?> testClass) {
        register(registry, testClass, false);
    }

    public static void register(DynamicPropertyRegistry registry, Class<?> testClass, boolean flywayMigration) {
        var schema = schemas.computeIfAbsent(testClass, ignored -> createSchema());
        registry.add("spring.datasource.url", schema::url);
        registry.add("spring.datasource.hikari.jdbc-url", schema::url);
        registry.add("spring.datasource.username", () -> ROLE);
        registry.add("spring.datasource.hikari.username", () -> ROLE);
        registry.add("spring.datasource.password", schema::password);
        registry.add("spring.datasource.hikari.password", schema::password);
        registry.add("spring.datasource.hikari.schema", schema::name);
        registry.add("spring.jpa.properties.hibernate.default_schema", schema::name);
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> flywayMigration ? "validate" : "create-only");
        registry.add("spring.flyway.enabled", () -> Boolean.toString(flywayMigration));
        registry.add("spring.flyway.url", schema::url);
        registry.add("spring.flyway.user", () -> ROLE);
        registry.add("spring.flyway.password", schema::password);
        registry.add("spring.flyway.default-schema", schema::name);
        registry.add("spring.flyway.schemas", schema::name);
        registry.add("spring.flyway.create-schemas", () -> "false");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    private static Schema createSchema() {
        String host = System.getenv().getOrDefault("USBSHIELD_TEST_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("USBSHIELD_TEST_DB_PORT", "5432");
        String username = System.getenv().getOrDefault("USBSHIELD_TEST_DB_USERNAME", ROLE);
        String password = System.getenv("USBSHIELD_TEST_DB_PASSWORD");
        if (!host.matches("[a-zA-Z0-9_.-]+") || !port.matches("[0-9]{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535
                || !ROLE.equals(username) || password == null || password.isBlank()) {
            throw new IllegalStateException("Integration tests require valid USBSHIELD_TEST_DB_HOST/PORT, "
                    + "USBSHIELD_TEST_DB_USERNAME=usbshield_test and USBSHIELD_TEST_DB_PASSWORD");
        }
        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/" + DATABASE;
        String name = "usbshield_it_" + UUID.randomUUID().toString().replace("-", "");
        assertSafeSchemaName(name);
        try (var db = DriverManager.getConnection(adminUrl, username, password);
             var check = db.createStatement();
             var metadata = check.executeQuery("select current_database(), current_user")) {
            if (!metadata.next() || !DATABASE.equals(metadata.getString(1)) || !ROLE.equals(metadata.getString(2))) {
                throw new IllegalStateException("Test JDBC connection did not resolve to the dedicated database/role");
            }
            try (var sql = db.createStatement()) { sql.execute("create schema " + name); }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot create isolated schema in usbshield_test as usbshield_test; "
                    + "check database CREATE privilege and test password", failure);
        }
        return new Schema(adminUrl + "?currentSchema=" + name, adminUrl, password, name);
    }

    static void cleanup(Class<?> testClass) throws SQLException {
        var schema = schemas.remove(testClass);
        if (schema == null) return;
        assertSafeSchemaName(schema.name());
        try (var db = DriverManager.getConnection(schema.adminUrl(), ROLE, schema.password());
             var stmt = db.createStatement();
             var check = stmt.executeQuery("select current_database(), current_user")) {
            if (!check.next() || !DATABASE.equals(check.getString(1)) || !ROLE.equals(check.getString(2))) {
                throw new SecurityException("Refusing test schema cleanup outside usbshield_test/usbshield_test");
            }
            // Only our random, class-owned schema can be removed. Never touch public or app DB.
            try (var drop = db.createStatement()) { drop.execute("drop schema if exists " + schema.name() + " cascade"); }
        }
    }

    static void assertSafeSchemaName(String name) {
        if (name == null || !name.matches("usbshield_it_[0-9a-f]{32}")) {
            throw new SecurityException("Refusing to modify an unrecognized integration-test schema");
        }
    }
}
