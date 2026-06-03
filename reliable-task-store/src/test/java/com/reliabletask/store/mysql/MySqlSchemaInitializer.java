package com.reliabletask.store.mysql;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 集成测试 schema 初始化工具。
 */
final class MySqlSchemaInitializer {

    private static final String SCHEMA_LOCATION = "db/schema.sql";

    private MySqlSchemaInitializer() {
    }

    static void initialize(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource(SCHEMA_LOCATION), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize MySQL integration test schema", e);
        }
    }
}
