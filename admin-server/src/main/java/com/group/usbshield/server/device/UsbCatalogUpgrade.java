package com.group.usbshield.server.device;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pre-Hibernate H2 catalog upgrade. Existing empty USBGuard hashes are not valid
 * identities and cannot participate in the new unique constraint. This hook is
 * wired as a dependency of entityManagerFactory, not an ApplicationRunner (which
 * would run only AFTER Hibernate's ddl-auto:update).
 */
@Configuration(proxyBeanMethods = false)
public class UsbCatalogUpgrade {
    private static final String PREPARE_BEAN = "usbCatalogUpgradePreflight";

    @Bean(PREPARE_BEAN)
    public Object preflight(DataSource dataSource) {
        normalizeAndValidate(dataSource);
        return new Object();
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor beforeHibernateDdl() {
        // Spring Boot's supported dependency hook works for any JPA factory,
        // including tests with a differently named persistence unit.
        return new EntityManagerFactoryDependsOnPostProcessor(PREPARE_BEAN);
    }

    static void normalizeAndValidate(DataSource source) {
        try (Connection connection = source.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!hasTable(metadata, connection.getCatalog(), connection.getSchema(), "USB_DEVICES")
                    || !hasColumn(metadata, connection.getCatalog(), connection.getSchema(), "USB_DEVICES", "DEVICE_HASH")) {
                // Brand new databases and old databases without a USB catalog
                // will be created/upgraded normally by Hibernate.
                return;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // SQL TRIM removes only a subset of Java whitespace. Match the
                // entity's String.isBlank() semantics exactly, including tabs,
                // newlines and Java-defined Unicode whitespace. Select IDs
                // first so the ResultSet is closed before updating the rows.
                List<Long> blankIds = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id, device_hash FROM usb_devices WHERE device_hash IS NOT NULL");
                     ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        String hash = rows.getString(2);
                        if (hash != null && hash.isBlank()) blankIds.add(rows.getLong(1));
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE usb_devices SET device_hash = NULL WHERE id = ?")) {
                    for (Long id : blankIds) {
                        update.setLong(1, id);
                        update.addBatch();
                    }
                    if (!blankIds.isEmpty()) update.executeBatch();
                }
                // Do not merge/drop remaining (nonblank) duplicates: existing
                // event/whitelist foreign keys can reference different rows.
                // Validate after the cleanup in the SAME transaction; any
                // duplicate rejection rolls back the normalization as well.
                try (Statement statement = connection.createStatement();
                     ResultSet duplicates = statement.executeQuery(
                             "SELECT COUNT(*) FROM (SELECT device_hash FROM usb_devices "
                             + "WHERE device_hash IS NOT NULL "
                             + "GROUP BY device_hash HAVING COUNT(*) > 1) duplicate_hashes")) {
                    duplicates.next();
                    long count = duplicates.getLong(1);
                    if (count > 0) {
                        throw new IllegalStateException("USB catalog upgrade blocked: " + count
                                + " duplicate nonblank device hashes. Back up the H2 database and reconcile "
                                + "the corresponding usb_devices and usb_events references before restarting.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("USB catalog pre-Hibernate upgrade failed; database left for review", failure);
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String schema, String name) throws SQLException {
        try (ResultSet tables = metadata.getTables(catalog, schema, null, null)) {
            while (tables.next()) {
                String type = tables.getString("TABLE_TYPE");
                if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))
                        && ("BASE TABLE".equalsIgnoreCase(type) || "TABLE".equalsIgnoreCase(type))) return true;
            }
            return false;
        }
    }

    private static boolean hasColumn(DatabaseMetaData metadata, String catalog, String schema,
                                     String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metadata.getColumns(catalog, schema, null, null)) {
            while (columns.next()) if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            return false;
        }
    }
}
