package com.reliabletask.store.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("schema.sql 测试")
class SchemaSqlTest {

    private static final String[] TASK_V2_TABLES = {
            "reliable_task",
            "reliable_task_log",
            "reliable_task_worker",
            "reliable_task_audit_log",
            "reliable_task_batch_operation"
    };

    @Test
    @DisplayName("schema.sql - MySQL mode 下可执行并包含 V2 表和索引")
    void schemaSql_executesAndContainsV2TablesAndIndexes() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:reliable_task_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")) {

            executeTaskV2Schema(connection);
            assertTableExists(connection, "reliable_task_worker");
            assertTableExists(connection, "reliable_task_audit_log");
            assertTableExists(connection, "reliable_task_batch_operation");
            assertColumnExists(connection, "reliable_task_log", "attempt_no");
            assertColumnExists(connection, "reliable_task_log", "status_before");
            assertColumnExists(connection, "reliable_task_log", "status_after");
            assertIndexExists(connection, "idx_status_finish_time");
            assertIndexExists(connection, "idx_worker_heartbeat");
            assertIndexExists(connection, "idx_audit_target");
            assertIndexExists(connection, "idx_audit_create_time");
            assertIndexExists(connection, "idx_batch_status_create_time");
        }
    }

    private void executeTaskV2Schema(Connection connection) throws Exception {
        String schema = readSchemaSql();
        try (Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                if (shouldExecute(sql)) {
                    statement.execute(sql);
                }
            }
        }
    }

    private String readSchemaSql() throws Exception {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("db/schema.sql");
        if (resource == null) {
            Path sourceSchema = Path.of("src", "main", "resources", "db", "schema.sql");
            return Files.readString(sourceSchema, StandardCharsets.UTF_8);
        }
        try (InputStream inputStream = resource) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private boolean shouldExecute(String sql) {
        String normalized = sql.toLowerCase();
        for (String table : TASK_V2_TABLES) {
            if (normalized.contains("create table if not exists `" + table + "`")) {
                return true;
            }
        }
        return false;
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            assertTrue(tables.next(), "Expected table to exist: " + tableName);
        }
    }

    private void assertColumnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertTrue(columns.next(), "Expected column to exist: " + tableName + "." + columnName);
        }
    }

    private void assertIndexExists(Connection connection, String indexName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery(
                     "select index_name from information_schema.indexes where index_name = '" + indexName + "'")) {
            assertTrue(indexes.next(), "Expected index to exist: " + indexName);
        }
    }
}
