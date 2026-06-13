package com.reliabletask.store.mysql;

import com.reliabletask.store.config.MyBatisPlusMetaObjectHandler;
import com.reliabletask.store.impl.MyBatisTaskStore;
import com.reliabletask.store.mapper.ReliableTaskAuditLogMapper;
import com.reliabletask.store.mapper.ReliableTaskBatchOperationMapper;
import com.reliabletask.store.mapper.ReliableTaskLogMapper;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import com.reliabletask.store.mapper.ReliableTaskWorkerMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@SpringBootTest(classes = AbstractMySqlIntegrationTest.TestApplication.class)
abstract class AbstractMySqlIntegrationTest {

    private static final String MODE_PROPERTY = "reliabletask.it.mysql.mode";
    private static final String MODE_ENV = "RELIABLE_TASK_IT_MYSQL_MODE";
    private static final String MODE_TESTCONTAINERS = "testcontainers";
    private static final String MODE_LOCAL = "local";
    private static final String LOCAL_URL_PROPERTY = "reliabletask.it.mysql.url";
    private static final String LOCAL_USERNAME_PROPERTY = "reliabletask.it.mysql.username";
    private static final String LOCAL_PASSWORD_PROPERTY = "reliabletask.it.mysql.password";
    private static final String LOCAL_URL_ENV = "RELIABLE_TASK_IT_JDBC_URL";
    private static final String LOCAL_USERNAME_ENV = "RELIABLE_TASK_IT_USERNAME";
    private static final String LOCAL_PASSWORD_ENV = "RELIABLE_TASK_IT_PASSWORD";
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");

    private static MySQLContainer<?> mysqlContainer;
    private static boolean shutdownHookRegistered;

    @Autowired
    private DataSource dataSource;

    @Autowired
    protected MyBatisTaskStore taskStore;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
        String mode = propertyOrEnv(MODE_PROPERTY, MODE_ENV, MODE_TESTCONTAINERS);
        if (MODE_LOCAL.equalsIgnoreCase(mode)) {
            String jdbcUrl = requiredPropertyOrEnv(LOCAL_URL_PROPERTY, LOCAL_URL_ENV);
            String username = requiredPropertyOrEnv(LOCAL_USERNAME_PROPERTY, LOCAL_USERNAME_ENV);
            String password = requiredPropertyOrEnv(LOCAL_PASSWORD_PROPERTY, LOCAL_PASSWORD_ENV);
            registry.add("spring.datasource.url", () -> jdbcUrl);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            registry.add("spring.datasource.driver-class-name",
                    () -> "com.mysql.cj.jdbc.Driver");
            return;
        }

        if (!MODE_TESTCONTAINERS.equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Unsupported MySQL integration test mode: " + mode);
        }

        MySQLContainer<?> container = startTestcontainersMySql();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", container::getDriverClassName);
    }

    protected void initializeSchema() {
        MySqlSchemaInitializer.initialize(dataSource);
        cleanTables();
    }

    private void cleanTables() {
        jdbcTemplate.update("DELETE FROM reliable_task_log");
        jdbcTemplate.update("DELETE FROM reliable_task_retry");
        jdbcTemplate.update("DELETE FROM reliable_task_audit_log");
        jdbcTemplate.update("DELETE FROM reliable_task_batch_operation");
        jdbcTemplate.update("DELETE FROM reliable_task_worker");
        jdbcTemplate.update("DELETE FROM reliable_task");
    }

    private static synchronized MySQLContainer<?> startTestcontainersMySql() {
        if (mysqlContainer == null) {
            mysqlContainer = new MySQLContainer<>(MYSQL_IMAGE)
                    .withDatabaseName("reliable_task_it")
                    .withUsername("reliable_task")
                    .withPassword("reliable_task");
            registerShutdownHook();
        }
        if (!mysqlContainer.isRunning()) {
            mysqlContainer.start();
        }
        return mysqlContainer;
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (mysqlContainer != null) {
                mysqlContainer.stop();
            }
        }, "reliable-task-mysql-it-shutdown"));
        shutdownHookRegistered = true;
    }

    private static String propertyOrEnv(String propertyName, String envName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (hasText(value)) {
            return value.trim();
        }
        value = System.getenv(envName);
        if (hasText(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    private static String requiredPropertyOrEnv(String propertyName, String envName) {
        String value = System.getProperty(propertyName);
        if (hasText(value)) {
            return value.trim();
        }
        value = System.getenv(envName);
        if (hasText(value)) {
            return value.trim();
        }
        throw new IllegalStateException("Missing MySQL integration test setting. Set system property "
                + propertyName + " or environment variable " + envName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !value.startsWith("${");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.reliabletask.store.mapper")
    @Import(MyBatisPlusMetaObjectHandler.class)
    static class TestApplication {

        @Bean
        MyBatisTaskStore myBatisTaskStore(ReliableTaskMapper taskMapper,
                                          ReliableTaskLogMapper taskLogMapper,
                                          ReliableTaskWorkerMapper workerMapper,
                                          ReliableTaskAuditLogMapper auditLogMapper,
                                          ReliableTaskBatchOperationMapper batchOperationMapper) {
            return new MyBatisTaskStore(taskMapper, taskLogMapper, workerMapper,
                    auditLogMapper, batchOperationMapper);
        }
    }
}
