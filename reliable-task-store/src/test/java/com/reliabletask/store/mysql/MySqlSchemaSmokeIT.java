package com.reliabletask.store.mysql;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlSchemaSmokeIT extends AbstractMySqlIntegrationTest {

    @BeforeEach
    void setUpSchema() {
        initializeSchema();
    }

    @Test
    @DisplayName("MySQL smoke - 加载 schema 后可保存并读取任务")
    void schemaLoadsAndTaskStoreCanSaveTask() {
        String bizId = "order-" + UUID.randomUUID();
        String bizUniqueKey = "MYSQL_SMOKE:ORDER:" + bizId;

        TaskInstance saved = taskStore.save(TaskInstance.builder()
                .taskType("MYSQL_SMOKE")
                .bizType("ORDER")
                .bizId(bizId)
                .bizUniqueKey(bizUniqueKey)
                .status(TaskStatus.PENDING)
                .priority(5)
                .payload("{\"source\":\"mysql-it\"}")
                .nextExecuteTime(LocalDateTime.now())
                .build());

        assertThat(saved.getId()).isNotNull();

        TaskInstance loaded = taskStore.getById(saved.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getBizUniqueKey()).isEqualTo(bizUniqueKey);
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(loaded.getPayload()).isEqualTo("{\"source\":\"mysql-it\"}");
    }
}
