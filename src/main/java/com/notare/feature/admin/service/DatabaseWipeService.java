package com.notare.feature.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Deletes all application data from the database, keeping the Flyway history. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseWipeService {

    private static final String FLYWAY_TABLE = "flyway_schema_history";

    private final DataSource dataSource;

    public void wipeAllData() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            List<String> tables = listTables(connection, product);

            if (tables.isEmpty()) {
                log.warn("Database wipe requested but no tables were found.");
                return;
            }

            if ("MySQL".equalsIgnoreCase(product)) {
                wipeMySql(connection, tables);
            } else if ("PostgreSQL".equalsIgnoreCase(product)) {
                wipePostgres(connection, tables);
            } else {
                throw new IllegalStateException("Database wipe is only implemented for MySQL and PostgreSQL, got: " + product);
            }

            log.warn("Database wipe complete — {} table(s) cleared: {}", tables.size(), tables);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to wipe database", e);
        }
    }

    private List<String> listTables(Connection connection, String product) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        String schema = "PostgreSQL".equalsIgnoreCase(product) ? "public" : null;

        try (ResultSet rs = metaData.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!FLYWAY_TABLE.equalsIgnoreCase(tableName)) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }

    private void wipeMySql(Connection connection, List<String> tables) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE `" + table + "`");
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private void wipePostgres(Connection connection, List<String> tables) throws SQLException {
        String quotedTables = tables.stream()
                .map(table -> "\"" + table + "\"")
                .collect(Collectors.joining(", "));

        try (Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE " + quotedTables + " RESTART IDENTITY CASCADE");
        }
    }
}
