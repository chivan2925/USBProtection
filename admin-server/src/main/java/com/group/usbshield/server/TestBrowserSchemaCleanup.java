package com.group.usbshield.server;

import java.sql.DriverManager;

/** Maintenance CLI for disposable browser test schemas ONLY. Never starts Spring HTTP/JPA.
 * The password stays in an environment variable and is never a JVM argument.
 */
public final class TestBrowserSchemaCleanup {
    private static final String ARG = "--usbshield.cleanup-browser-test-schema=";
    private TestBrowserSchemaCleanup() {}

    static boolean handle(String[] args) throws Exception {
        if (args.length == 0 || !args[0].startsWith(ARG)) return false;
        if (args.length != 1) throw new SecurityException("Browser cleanup accepts exactly one argument");
        String schema = args[0].substring(ARG.length());
        assertAllowedSchema(schema);
        String role = System.getenv().getOrDefault("USBSHIELD_TEST_DB_USERNAME", "usbshield_test");
        String password = System.getenv("USBSHIELD_TEST_DB_PASSWORD");
        String host = System.getenv().getOrDefault("USBSHIELD_TEST_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("USBSHIELD_TEST_DB_PORT", "5432");
        if (!"usbshield_test".equals(role) || password == null || password.isBlank()
                || !host.matches("[a-zA-Z0-9_.-]+") || !port.matches("[0-9]{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new SecurityException("Browser cleanup requires the dedicated test role, password and valid host/port");
        }
        String url = "jdbc:postgresql://" + host + ":" + port + "/usbshield_browser_test";
        try (var connection = DriverManager.getConnection(url, role, password);
             var statement = connection.createStatement();
             var actual = statement.executeQuery("select current_database(), current_user")) {
            if (!actual.next() || !"usbshield_browser_test".equals(actual.getString(1))
                    || !"usbshield_test".equals(actual.getString(2))) {
                throw new SecurityException("Refusing cleanup outside usbshield_browser_test/usbshield_test");
            }
            try (var delete = connection.createStatement()) {
                delete.execute("drop schema if exists " + schema + " cascade");
            }
        }
        System.out.println("Browser test schema cleanup completed: " + schema);
        return true;
    }

    static void assertAllowedSchema(String schema) {
        if (schema == null || !schema.matches("usbshield_e2e_[0-9a-f]{32}")) {
            throw new SecurityException("Refusing to drop non-browser-test schema");
        }
    }
}
