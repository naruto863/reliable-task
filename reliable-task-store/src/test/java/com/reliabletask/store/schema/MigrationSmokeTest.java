package com.reliabletask.store.schema;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("数据库初始化迁移 smoke test")
class MigrationSmokeTest {

    private static final String[] REQUIRED_TABLES = {
            "reliable_task",
            "reliable_task_log",
            "reliable_task_retry",
            "reliable_task_worker",
            "reliable_task_audit_log",
            "reliable_task_batch_operation"
    };

    private static final String[] REQUIRED_INDEXES = {
            "uk_biz_unique_key",
            "idx_status_next_priority_id",
            "idx_lock_expire",
            "idx_task_type",
            "idx_biz_type_biz_id",
            "idx_tenant_id",
            "idx_create_time",
            "idx_status_finish_time",
            "idx_task_log_create_time",
            "idx_retry_task_id",
            "idx_worker_heartbeat",
            "idx_audit_target",
            "idx_audit_create_time",
            "idx_audit_task_id",
            "idx_batch_status_create_time",
            "idx_batch_operator_create_time"
    };

    @Test
    @DisplayName("schema.sql 与 Flyway V1 初始脚本保持一致")
    void schemaSqlAndFlywayV1_areByteIdentical() throws Exception {
        String schemaSql = readSqlResource("db/schema.sql");
        String flywayV1 = readSqlResource("db/migration/V1__init_reliable_task_schema.sql");

        assertEquals(schemaSql, flywayV1, "schema.sql and Flyway V1 must describe the same v1.0 baseline");
    }

    @Test
    @DisplayName("schema.sql 在 H2 MySQL mode 下可执行")
    void schemaSql_executesInH2MysqlMode() throws Exception {
        try (Connection connection = openConnection("schema_sql")) {
            executeSqlResource(connection, "db/schema.sql");

            assertRequiredObjects(connection);
        }
    }

    @Test
    @DisplayName("Flyway 初始脚本在 H2 MySQL mode 下可执行")
    void flywayMigration_executesInH2MysqlMode() throws Exception {
        String jdbcUrl = h2JdbcUrl("flyway");
        Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertRequiredObjects(connection);
        }
    }

    @Test
    @DisplayName("Liquibase 初始 changelog 在 H2 MySQL mode 下可执行")
    void liquibaseChangelog_executesInH2MysqlMode() throws Exception {
        String jdbcUrl = h2JdbcUrl("liquibase");
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertRequiredObjects(connection);
        }
    }

    private Connection openConnection(String databaseName) throws SQLException {
        return DriverManager.getConnection(h2JdbcUrl(databaseName));
    }

    private String h2JdbcUrl(String databaseName) {
        return "jdbc:h2:mem:reliable_task_" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private void executeSqlResource(Connection connection, String resourcePath) throws Exception {
        String sqlScript = readSqlResource(resourcePath);
        try (Statement statement = connection.createStatement()) {
            for (String sql : sqlScript.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private String readSqlResource(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertRequiredObjects(Connection connection) throws SQLException {
        for (String table : REQUIRED_TABLES) {
            assertTableExists(connection, table);
        }
        for (String index : REQUIRED_INDEXES) {
            assertIndexExists(connection, index);
        }
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            assertTrue(tables.next(), "Expected table to exist: " + tableName);
        }
    }

    private void assertIndexExists(Connection connection, String indexName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery(
                     "select index_name from information_schema.indexes where index_name = '" + indexName + "' "
                             + "union all "
                             + "select constraint_name from information_schema.table_constraints "
                             + "where constraint_name = '" + indexName + "'")) {
            assertTrue(indexes.next(), "Expected index to exist: " + indexName);
        }
    }
}
