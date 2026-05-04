package com.reliabletask.core.spi;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.noop.NoopIdempotencyStrategy;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.spi.noop.NoopWorkerHeartbeatReporter;
import com.reliabletask.core.spi.noop.StringTaskPayloadSerializer;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("V2 core SPI 测试")
class V2CoreSpiTest {

    @Test
    @DisplayName("NoopIdempotencyStrategy - 无已有任务时允许创建")
    void noopIdempotencyStrategy_withoutExistingTask_createsNew() {
        NoopIdempotencyStrategy strategy = new NoopIdempotencyStrategy();

        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .bizUniqueKey("TYPE:BIZ:ID")
                .build());

        assertEquals(NoopIdempotencyStrategy.NAME, strategy.getName());
        assertTrue(decision.shouldCreateNew());
    }

    @Test
    @DisplayName("NoopIdempotencyStrategy - 已有任务时返回已有任务")
    void noopIdempotencyStrategy_withExistingTask_returnsExisting() {
        NoopIdempotencyStrategy strategy = new NoopIdempotencyStrategy();

        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .existingTask(TaskInstance.builder().id(100L).build())
                .build());

        assertEquals(IdempotencyDecision.Action.RETURN_EXISTING, decision.getAction());
        assertEquals(100L, decision.getExistingTaskId());
        assertFalse(decision.shouldCreateNew());
    }

    @Test
    @DisplayName("StrictUniqueIdempotencyStrategy - 已有任务时返回已有任务")
    void strictUniqueIdempotencyStrategy_withExistingTask_returnsExisting() {
        StrictUniqueIdempotencyStrategy strategy = new StrictUniqueIdempotencyStrategy();

        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .bizUniqueKey("TYPE:BIZ:ID")
                .existingTask(TaskInstance.builder().id(10L).status(TaskStatus.SUCCESS).build())
                .build());

        assertEquals(StrictUniqueIdempotencyStrategy.NAME, strategy.getName());
        assertEquals(IdempotencyDecision.Action.RETURN_EXISTING, decision.getAction());
        assertEquals(10L, decision.getExistingTaskId());
    }

    @Test
    @DisplayName("AllowAfterTerminalIdempotencyStrategy - 终态后使用新幂等键")
    void allowAfterTerminalIdempotencyStrategy_terminalTask_createsNewKey() {
        AllowAfterTerminalIdempotencyStrategy strategy = new AllowAfterTerminalIdempotencyStrategy();

        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .bizUniqueKey("TYPE:BIZ:ID")
                .existingTask(TaskInstance.builder().id(10L).status(TaskStatus.DEAD).build())
                .build());

        assertEquals(AllowAfterTerminalIdempotencyStrategy.NAME, strategy.getName());
        assertTrue(decision.shouldCreateNew());
        assertTrue(decision.getBizUniqueKey().startsWith("TYPE:BIZ:ID:RERUN:10:"));
    }

    @Test
    @DisplayName("AllowAfterTerminalIdempotencyStrategy - 非终态返回已有任务")
    void allowAfterTerminalIdempotencyStrategy_nonTerminalTask_returnsExisting() {
        AllowAfterTerminalIdempotencyStrategy strategy = new AllowAfterTerminalIdempotencyStrategy();

        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .bizUniqueKey("TYPE:BIZ:ID")
                .existingTask(TaskInstance.builder().id(10L).status(TaskStatus.PENDING).build())
                .build());

        assertEquals(IdempotencyDecision.Action.RETURN_EXISTING, decision.getAction());
        assertEquals(10L, decision.getExistingTaskId());
    }

    @Test
    @DisplayName("StringTaskPayloadSerializer - 字符串 payload 透传")
    void stringTaskPayloadSerializer_passesThroughStringPayload() {
        StringTaskPayloadSerializer serializer = new StringTaskPayloadSerializer();

        String payload = serializer.serialize("{\"id\":1}");

        assertEquals("{\"id\":1}", payload);
        assertEquals("{\"id\":1}", serializer.deserialize(payload, String.class));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(payload, Object.class));
    }

    @Test
    @DisplayName("Noop SPI - 默认实现不抛异常且权限允许")
    void noopSpiDefaults_areCompatible() {
        assertTrue(new NoopTaskAuthorizationProvider().isAllowed("admin", "TASK_RETRY", 1L));

        new NoopTaskAuditRecorder().record(AuditLog.builder()
                .operationType("TASK_RETRY")
                .targetType("TASK")
                .targetId("1")
                .result("SUCCESS")
                .build());
        new NoopWorkerHeartbeatReporter().report(WorkerHeartbeat.builder()
                .workerId("worker-1")
                .lastHeartbeatTime(LocalDateTime.now())
                .build());
        new NoopTaskMetricsRecorder().record(TaskExecutionMetricsEvent.builder()
                .taskId(1L)
                .taskType("TYPE_A")
                .status(TaskStatus.SUCCESS)
                .success(true)
                .durationMs(10L)
                .eventTime(LocalDateTime.now())
                .build());
    }
}
