package com.reliabletask.core.model;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskInstance 领域模型测试")
class TaskInstanceTest {

    @Test
    @DisplayName("canRetry - 实际重试次数未达到最大重试次数时可以重试")
    void canRetry_whenActualRetryCountLessThanMax_returnsTrue() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(3)
                .maxRetryCount(3)
                .build();
        assertTrue(task.canRetry());
    }

    @Test
    @DisplayName("canRetry - 实际重试次数达到最大重试次数时不可重试")
    void canRetry_whenActualRetryCountEqualsMax_returnsFalse() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(4)
                .maxRetryCount(3)
                .build();
        assertFalse(task.canRetry());
    }

    @Test
    @DisplayName("canRetry - 超过最大重试次数时不可重试")
    void canRetry_whenExecuteCountExceedsMax_returnsFalse() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(5)
                .maxRetryCount(3)
                .build();
        assertFalse(task.canRetry());
    }

    @Test
    @DisplayName("canRetry - 尚未执行时可以重试")
    void canRetry_whenNeverExecuted_returnsTrue() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(0)
                .maxRetryCount(3)
                .build();
        assertTrue(task.canRetry());
    }

    @Test
    @DisplayName("canRetry - 最大重试次数为 0 时不可重试")
    void canRetry_whenMaxRetryCountZero_returnsFalse() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(1)
                .maxRetryCount(0)
                .build();
        assertFalse(task.canRetry());
    }

    @Test
    @DisplayName("getActualRetryCount - 执行 1 次（首次），重试 0 次")
    void getActualRetryCount_afterFirstExecution_returnsZero() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(1)
                .build();
        assertEquals(0, task.getActualRetryCount());
    }

    @Test
    @DisplayName("getActualRetryCount - 执行 4 次（首次 + 3 次重试），重试 3 次")
    void getActualRetryCount_afterMultipleRetries_returnsCorrectCount() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(4)
                .build();
        assertEquals(3, task.getActualRetryCount());
    }

    @Test
    @DisplayName("getActualRetryCount - 尚未执行，重试次数为 0")
    void getActualRetryCount_whenNeverExecuted_returnsZero() {
        TaskInstance task = TaskInstance.builder()
                .executeCount(0)
                .build();
        assertEquals(0, task.getActualRetryCount());
    }

    @Test
    @DisplayName("Builder - 使用默认值构建")
    void builder_withDefaults_usesDefaultValues() {
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("TEST")
                .bizType("ORDER")
                .bizId("ORD-001")
                .payload("{}")
                .build();

        assertEquals(5, request.getPriority());
        assertEquals(3, request.getMaxRetryCount());
        assertEquals(RetryStrategyType.EXPONENTIAL, request.getRetryStrategy());
        assertEquals(1000L, request.getRetryIntervalMs());
    }

    @Test
    @DisplayName("Builder - 使用自定义值构建")
    void builder_withCustomValues_usesProvidedValues() {
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-001")
                .payload("{\"orderId\":\"ORD-001\"}")
                .priority(1)
                .maxRetryCount(5)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(2000L)
                .idempotencyKey("shipment:ORD-001")
                .shardKey("user-123")
                .tenantId("tenant-1")
                .build();

        assertEquals("CREATE_SHIPMENT", request.getTaskType());
        assertEquals("shipment:ORD-001", request.getIdempotencyKey());
        assertEquals(1, request.getPriority());
        assertEquals(5, request.getMaxRetryCount());
        assertEquals(RetryStrategyType.FIXED, request.getRetryStrategy());
        assertEquals(2000L, request.getRetryIntervalMs());
        assertEquals("user-123", request.getShardKey());
        assertEquals("tenant-1", request.getTenantId());
    }
}
