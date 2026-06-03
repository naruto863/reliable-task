package com.reliabletask.executor.mysql;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlTaskTemplateIT extends AbstractMySqlTaskTemplateIntegrationTest {

    @BeforeEach
    void setUpSchema() {
        initializeSchema();
    }

    @Test
    @DisplayName("MySQL template - 重复投递返回已有任务")
    void duplicateSubmitReturnsExistingTask() {
        String bizId = uniqueBizId("DUP");
        TaskSubmitRequest request = request("MYSQL_TEMPLATE_DUP", bizId,
                "{\"orderId\":\"" + bizId + "\"}");

        TaskSubmitResult first = taskTemplate.submitForResult(request);
        TaskSubmitResult second = taskTemplate.submitForResult(request);

        assertThat(first.isCreated()).isTrue();
        assertThat(first.isExisting()).isFalse();
        assertThat(second.isCreated()).isFalse();
        assertThat(second.isExisting()).isTrue();
        assertThat(second.getTaskId()).isEqualTo(first.getTaskId());
        assertThat(countByBizUniqueKey(first.getBizUniqueKey())).isEqualTo(1L);
    }

    @Test
    @DisplayName("MySQL template - 显式 idempotencyKey 跨 bizId 去重")
    void explicitIdempotencyKeyDeduplicatesAcrossBizIds() {
        String idempotencyKey = "mysql-template-explicit-" + UUID.randomUUID();
        TaskSubmitRequest firstRequest = request("MYSQL_TEMPLATE_EXPLICIT", uniqueBizId("EXPLICIT_A"),
                "{\"source\":\"A\"}");
        firstRequest.setIdempotencyKey(idempotencyKey);
        TaskSubmitRequest secondRequest = request("MYSQL_TEMPLATE_EXPLICIT", uniqueBizId("EXPLICIT_B"),
                "{\"source\":\"B\"}");
        secondRequest.setIdempotencyKey(idempotencyKey);

        TaskSubmitResult first = taskTemplate.submitForResult(firstRequest);
        TaskSubmitResult second = taskTemplate.submitForResult(secondRequest);

        assertThat(first.isCreated()).isTrue();
        assertThat(second.isExisting()).isTrue();
        assertThat(second.getTaskId()).isEqualTo(first.getTaskId());
        assertThat(first.getBizUniqueKey()).isEqualTo(idempotencyKey);
        assertThat(countByBizUniqueKey(idempotencyKey)).isEqualTo(1L);
    }

    @Test
    @DisplayName("MySQL template - 对象 payload 序列化后入库")
    void objectPayloadIsPersistedAsJson() {
        String bizId = uniqueBizId("PAYLOAD");
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("MYSQL_TEMPLATE_PAYLOAD")
                .bizType("ORDER")
                .bizId(bizId)
                .payloadObject(new ShipmentPayload(bizId, 7))
                .build();

        TaskSubmitResult result = taskTemplate.submitForResult(request);
        TaskInstance loaded = taskStore.getById(result.getTaskId());

        assertThat(result.isCreated()).isTrue();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(loaded.getPayload())
                .isEqualTo("{\"orderId\":\"" + bizId + "\",\"quantity\":7}");
    }

    @Test
    @DisplayName("MySQL template - 外层事务回滚后任务不落库")
    void rollbackDoesNotPersistTask() {
        String bizId = uniqueBizId("ROLLBACK");
        TaskSubmitRequest request = request("MYSQL_TEMPLATE_ROLLBACK", bizId,
                "{\"orderId\":\"" + bizId + "\"}");
        String bizUniqueKey = "MYSQL_TEMPLATE_ROLLBACK:ORDER:" + bizId;
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            taskTemplate.submitForResult(request);
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(taskStore.getByBizUniqueKey(bizUniqueKey)).isNull();
        assertThat(countByBizUniqueKey(bizUniqueKey)).isZero();
    }

    private TaskSubmitRequest request(String taskType, String bizId, String payload) {
        return TaskSubmitRequest.builder()
                .taskType(taskType)
                .bizType("ORDER")
                .bizId(bizId)
                .payload(payload)
                .build();
    }

    private String uniqueBizId(String suffix) {
        return "MYSQL_TEMPLATE_IT_" + suffix + "_" + UUID.randomUUID();
    }

    private Long countByBizUniqueKey(String bizUniqueKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reliable_task WHERE biz_unique_key = ?",
                Long.class,
                bizUniqueKey);
    }

    record ShipmentPayload(String orderId, int quantity) {
    }

    private static class RollbackProbeException extends RuntimeException {
    }
}
