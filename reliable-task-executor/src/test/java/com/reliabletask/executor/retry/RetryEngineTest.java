package com.reliabletask.executor.retry;

import com.reliabletask.core.annotation.TaskRetryable;
import com.reliabletask.core.diagnostics.TaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskFailureDiagnostic;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.exception.RetryableException;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.FailureClassifier;
import com.reliabletask.core.spi.RetryStrategy;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.strategy.RetryStrategyRegistry;
import com.reliabletask.executor.alert.TaskAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RetryEngine 测试")
@ExtendWith(MockitoExtension.class)
class RetryEngineTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private TaskMetricsRecorder metricsRecorder;

    @Mock
    private TaskAuditRecorder auditRecorder;

    private RetryEngine retryEngine;

    @BeforeEach
    void setUp() {
        lenient().when(taskStore.markDead(anyLong(), anyString(), anyString())).thenReturn(true);
        lenient().when(taskStore.markWaitRetry(anyLong(), anyString(), anyString(), any())).thenReturn(true);
        retryEngine = new RetryEngine(taskStore);
    }

    @Test
    @DisplayName("handleFailure - 可重试异常标记 RETRYING")
    void handleFailure_retryableException_marksRetrying() {
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder);
        TaskInstance task = TaskInstance.builder()
                .id(1L).taskType("TYPE_A").bizId("BIZ-1")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .workerId("worker-1").traceId("trace-1")
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 500L, "RUNNING");

        verify(taskStore).markWaitRetry(eq(1L), eq("RetryableException"), anyString(), any(LocalDateTime.class));
        verify(taskStore).saveLog(eq(1L), eq(1), eq("RUNNING"), eq("RETRYING"),
                eq(false), eq(500L), eq("RetryableException"), anyString(), eq("worker-1"), eq("trace-1"));
        ArgumentCaptor<TaskExecutionMetricsEvent> metricsCaptor =
                ArgumentCaptor.forClass(TaskExecutionMetricsEvent.class);
        verify(metricsRecorder).record(metricsCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(com.reliabletask.core.enums.TaskStatus.RETRYING,
                metricsCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("RetryableException",
                metricsCaptor.getValue().getErrorCode());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRecorder).record(auditCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM_RETRY_SCHEDULED",
                auditCaptor.getValue().getOperationType());
    }

    @Test
    @DisplayName("handleFailure - RETRYING 回写成功后发布 RETRY_SCHEDULED 事件")
    void handleFailure_retryableException_publishesRetryScheduledEvent() {
        List<TaskEvent> events = new ArrayList<>();
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, new RetryStrategyRegistry(), new RetryProperties(), null,
                new TaskEventPublisher(List.of(events::add)));
        TaskInstance task = TaskInstance.builder()
                .id(32L).taskType("TYPE_A").bizId("BIZ-32")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .workerId("worker-32").traceId("trace-32")
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 500L, "RUNNING");

        assertEquals(1, events.size());
        TaskEvent event = events.get(0);
        assertEquals(TaskEventType.RETRY_SCHEDULED, event.getEventType());
        assertEquals(TaskStatus.RUNNING, event.getStatusBefore());
        assertEquals(TaskStatus.RETRYING, event.getStatusAfter());
        assertEquals("RetryableException", event.getReason());
    }

    @Test
    @DisplayName("handleFailure - RETRYING 回写优先使用执行租约")
    void handleFailure_retryableExceptionUsesExecutionLease() {
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder);
        TaskInstance task = leasedTask(11L, 1, 3);
        when(taskStore.markWaitRetry(any(TaskExecutionLease.class), anyString(), anyString(), any()))
                .thenReturn(true);

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 500L, "RUNNING");

        verify(taskStore).markWaitRetry(argThat((TaskExecutionLease lease) ->
                        lease.getTaskId().equals(11L)
                                && "worker-11".equals(lease.getWorkerId())
                                && Integer.valueOf(2).equals(lease.getVersion())),
                eq("RetryableException"), anyString(), any(LocalDateTime.class));
        verify(taskStore, never()).markWaitRetry(eq(11L), anyString(), anyString(), any(LocalDateTime.class));
        verify(taskStore).saveLog(eq(11L), eq(1), eq("RUNNING"), eq("RETRYING"),
                eq(false), eq(500L), eq("RetryableException"), anyString(), eq("worker-11"), eq("trace-11"));
    }

    @Test
    @DisplayName("handleFailure - RETRYING CAS 失败不写执行日志")
    void handleFailure_retryingLeaseCasFailureSkipsLog() {
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder);
        TaskInstance task = leasedTask(12L, 1, 3);
        when(taskStore.markWaitRetry(any(TaskExecutionLease.class), anyString(), anyString(), any()))
                .thenReturn(false);

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 500L, "RUNNING");

        verify(taskStore).markWaitRetry(any(TaskExecutionLease.class), eq("RetryableException"),
                anyString(), any(LocalDateTime.class));
        verify(taskStore, never()).markWaitRetry(eq(12L), anyString(), anyString(), any(LocalDateTime.class));
        verify(taskStore, never()).saveLog(anyLong(), anyInt(), anyString(), anyString(),
                anyBoolean(), anyLong(), anyString(), anyString(), any(), any());
        verifyNoInteractions(metricsRecorder, auditRecorder);
    }

    @Test
    @DisplayName("handleFailure - 不可重试异常标记 DEAD")
    void handleFailure_nonRetryableException_marksDead() {
        TaskAlertService alertService = mock(TaskAlertService.class);
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, alertService);
        TaskInstance task = TaskInstance.builder()
                .id(2L).taskType("TYPE_A").bizId("BIZ-2")
                .executeCount(1).maxRetryCount(3)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new NonRetryableException("invalid data"), 200L, "RUNNING");

        verify(taskStore).markDead(eq(2L), eq("NonRetryableException"), anyString());
        verify(taskStore).saveLog(eq(2L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), eq(200L), eq("NonRetryableException"), anyString(), isNull(), isNull());
        verify(metricsRecorder).record(argThat(event ->
                event.getStatus() == com.reliabletask.core.enums.TaskStatus.DEAD
                        && "NonRetryableException".equals(event.getErrorCode())));
        verify(auditRecorder).record(argThat(audit ->
                "SYSTEM_NON_RETRYABLE_DEAD".equals(audit.getOperationType())));
        verify(alertService).notifyDead(eq(task), contains("non-retryable"));
    }

    @Test
    @DisplayName("handleFailure - DEAD 回写优先使用执行租约")
    void handleFailure_nonRetryableExceptionUsesExecutionLease() {
        TaskAlertService alertService = mock(TaskAlertService.class);
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, alertService);
        TaskInstance task = leasedTask(13L, 1, 3);
        when(taskStore.markDead(any(TaskExecutionLease.class), anyString(), anyString()))
                .thenReturn(true);

        retryEngine.handleFailure(new DefaultHandler(), task,
                new NonRetryableException("invalid data"), 200L, "RUNNING");

        verify(taskStore).markDead(argThat((TaskExecutionLease lease) ->
                        lease.getTaskId().equals(13L)
                                && "worker-13".equals(lease.getWorkerId())
                                && Integer.valueOf(2).equals(lease.getVersion())),
                eq("NonRetryableException"), anyString());
        verify(taskStore, never()).markDead(eq(13L), anyString(), anyString());
        verify(taskStore).saveLog(eq(13L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), eq(200L), eq("NonRetryableException"), anyString(), eq("worker-13"), eq("trace-13"));
    }

    @Test
    @DisplayName("handleFailure - 使用异常格式化 SPI 写入诊断信息")
    void handleFailure_usesExceptionFormatterDiagnostic() {
        TaskAlertService alertService = mock(TaskAlertService.class);
        TaskExceptionFormatter formatter = error ->
                new TaskFailureDiagnostic("REMOTE_TIMEOUT", "masked summary", "compressed stack");
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, alertService, formatter);
        TaskInstance task = TaskInstance.builder()
                .id(21L).taskType("TYPE_A").bizId("BIZ-21")
                .executeCount(1).maxRetryCount(0)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RuntimeException("raw internal message"), 200L, "RUNNING");

        verify(taskStore).markDead(21L, "REMOTE_TIMEOUT", "masked summary");
        verify(taskStore).saveLog(eq(21L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), eq(200L), eq("REMOTE_TIMEOUT"), eq("compressed stack"), isNull(), isNull());
        verify(metricsRecorder).record(argThat(event ->
                "REMOTE_TIMEOUT".equals(event.getErrorCode())));
    }

    @Test
    @DisplayName("handleFailure - 自定义 FailureClassifier 可将可重试异常判定为 DEAD")
    void handleFailure_customFailureClassifierCanMarkDead() {
        FailureClassifier classifier = (task, error) -> FailureDecision.dead("remote 4xx");
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, new RetryStrategyRegistry(), new RetryProperties(), classifier);
        TaskInstance task = TaskInstance.builder()
                .id(26L).taskType("TYPE_A").bizId("BIZ-26")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("remote rejected"), 200L, "RUNNING");

        verify(taskStore).markDead(eq(26L), eq("RetryableException"), anyString());
        verify(taskStore, never()).markWaitRetry(eq(26L), anyString(), anyString(), any());
        verify(auditRecorder).record(argThat(audit ->
                "SYSTEM_FAILURE_CLASSIFIED_DEAD".equals(audit.getOperationType())
                        && audit.getErrorMsg().contains("remote 4xx")));
    }

    @Test
    @DisplayName("handleFailure - 自定义 FailureClassifier 可将 NonRetryableException 判定为 RETRY")
    void handleFailure_customFailureClassifierCanRetryNonRetryableException() {
        FailureClassifier classifier = (task, error) -> FailureDecision.retry("override");
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, new RetryStrategyRegistry(), new RetryProperties(), classifier);
        TaskInstance task = TaskInstance.builder()
                .id(27L).taskType("TYPE_A").bizId("BIZ-27")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new NonRetryableException("invalid but overridden"), 200L, "RUNNING");

        verify(taskStore).markWaitRetry(eq(27L), eq("NonRetryableException"), anyString(), any(LocalDateTime.class));
        verify(taskStore, never()).markDead(eq(27L), anyString(), anyString());
    }

    @Test
    @DisplayName("handleFailure - FailureClassifier 异常时 fallback 到默认分类")
    void handleFailure_failureClassifierThrows_fallsBackToDefaultClassifier() {
        FailureClassifier classifier = (task, error) -> {
            throw new IllegalStateException("classifier down");
        };
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, new RetryStrategyRegistry(), new RetryProperties(), classifier);
        TaskInstance task = TaskInstance.builder()
                .id(28L).taskType("TYPE_A").bizId("BIZ-28")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new NonRetryableException("invalid"), 200L, "RUNNING");

        verify(taskStore).markDead(eq(28L), eq("NonRetryableException"), anyString());
    }

    @Test
    @DisplayName("handleFailure - DEAD 告警异常不影响状态流转")
    void handleFailure_deadAlertThrows_doesNotBreakStateTransition() {
        TaskAlertService alertService = mock(TaskAlertService.class);
        doThrow(new RuntimeException("notify failed")).when(alertService)
                .notifyDead(any(TaskInstance.class), anyString());
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, alertService);
        TaskInstance task = TaskInstance.builder()
                .id(20L).taskType("TYPE_A").bizId("BIZ-20")
                .executeCount(1).maxRetryCount(0)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new NonRetryableException("invalid data"), 200L, "RUNNING");

        verify(taskStore).markDead(eq(20L), eq("NonRetryableException"), anyString());
        verify(taskStore).saveLog(eq(20L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), eq(200L), eq("NonRetryableException"), anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("handleFailure - 超过最大重试次数标记 DEAD")
    void handleFailure_retriesExhausted_marksDead() {
        TaskInstance task = TaskInstance.builder()
                .id(3L).taskType("TYPE_A").bizId("BIZ-3")
                .executeCount(4).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("still failing"), 300L, "RUNNING");

        verify(taskStore).markDead(eq(3L), eq("RetryableException"), anyString());
    }

    @Test
    @DisplayName("handleFailure - 解包 CompletionException 找到根异常")
    void handleFailure_unwrapsCompletionException() {
        TaskInstance task = TaskInstance.builder()
                .id(4L).taskType("TYPE_A").bizId("BIZ-4")
                .executeCount(1).maxRetryCount(3)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new CompletionException(new NonRetryableException("wrapped")), 100L, "RUNNING");

        verify(taskStore).markDead(eq(4L), eq("NonRetryableException"), anyString());
    }

    @Test
    @DisplayName("handleFailure - 解包 ExecutionException 找到根异常")
    void handleFailure_unwrapsExecutionException() {
        TaskInstance task = TaskInstance.builder()
                .id(5L).taskType("TYPE_A").bizId("BIZ-5")
                .executeCount(1).maxRetryCount(3)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new ExecutionException(new NonRetryableException("wrapped")), 100L, "RUNNING");

        verify(taskStore).markDead(eq(5L), eq("NonRetryableException"), anyString());
    }

    @Test
    @DisplayName("handleFailure - 注解 maxDelayMs 生效")
    void handleFailure_annotationMaxDelayMs() {
        TaskInstance task = TaskInstance.builder()
                .id(6L).taskType("TYPE_A").bizId("BIZ-6")
                .executeCount(1).maxRetryCount(5)
                .retryStrategy(RetryStrategyType.EXPONENTIAL).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new AnnotatedHandler(), task,
                new RuntimeException("fail"), 100L, "RUNNING");

        verify(taskStore).markWaitRetry(eq(6L), eq("RuntimeException"), anyString(), argThat(nextTime -> {
            long delayMs = java.time.Duration.between(LocalDateTime.now(), nextTime).toMillis();
            return delayMs <= 5000L;
        }));
    }

    @Test
    @DisplayName("handleFailure - CUSTOM 使用注册的自定义重试策略")
    void handleFailure_customStrategy_usesRegisteredStrategy() {
        RetryStrategyRegistry registry = new RetryStrategyRegistry(List.of(new ConstantDelayRetryStrategy(60_000L)));
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, registry, new RetryProperties());
        TaskInstance task = TaskInstance.builder()
                .id(30L).taskType("TYPE_A").bizId("BIZ-30")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.CUSTOM).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 100L, "RUNNING");

        verify(taskStore).markWaitRetry(eq(30L), eq("RetryableException"), anyString(), argThat(nextTime -> {
            long delayMs = Duration.between(LocalDateTime.now(), nextTime).toMillis();
            return delayMs >= 59_000L && delayMs <= 61_000L;
        }));
    }

    @Test
    @DisplayName("handleFailure - 全局 minDelayMs 对过小延迟生效")
    void handleFailure_minDelayMs_clampsTooSmallDelay() {
        RetryProperties retryProperties = new RetryProperties();
        retryProperties.setMinDelayMs(5_000L);
        retryProperties.setMaxDelayMs(60_000L);
        retryEngine = new RetryEngine(taskStore, metricsRecorder, auditRecorder, mock(TaskAlertService.class),
                null, new RetryStrategyRegistry(), retryProperties);
        TaskInstance task = TaskInstance.builder()
                .id(31L).taskType("TYPE_A").bizId("BIZ-31")
                .executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(100L)
                .build();

        retryEngine.handleFailure(new DefaultHandler(), task,
                new RetryableException("temporary"), 100L, "RUNNING");

        verify(taskStore).markWaitRetry(eq(31L), eq("RetryableException"), anyString(), argThat(nextTime -> {
            long delayMs = Duration.between(LocalDateTime.now(), nextTime).toMillis();
            return delayMs >= 4_500L && delayMs <= 5_500L;
        }));
    }

    @Test
    @DisplayName("handleFailure - 注解 maxRetryCount 覆盖 TaskInstance")
    void handleFailure_annotationMaxRetryCount_overridesTaskInstance() {
        // TaskInstance says maxRetryCount=10, annotation says 2
        // executeCount=3 means first attempt + 2 retries, so annotation maxRetryCount=2 is exhausted.
        TaskInstance task = TaskInstance.builder()
                .id(7L).taskType("TYPE_A").bizId("BIZ-7")
                .executeCount(3).maxRetryCount(10)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        retryEngine.handleFailure(new AnnotatedHandler(), task,
                new RetryableException("fail"), 100L, "RUNNING");

        // Annotation maxRetryCount=2, actualRetryCount=2 → DEAD
        verify(taskStore).markDead(eq(7L), eq("RetryableException"), anyString());
    }

    // ==================== Test Handlers ====================

    static class DefaultHandler implements TaskHandler {
        public String getTaskType() { return "TYPE_A"; }
        public void execute(TaskInstance task) { }
    }

    static class ConstantDelayRetryStrategy implements RetryStrategy {
        private final long delayMs;

        ConstantDelayRetryStrategy(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public RetryStrategyType getType() {
            return RetryStrategyType.CUSTOM;
        }

        @Override
        public long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs) {
            return delayMs;
        }
    }

    @TaskRetryable(maxRetryCount = 2, retryIntervalMs = 500, maxDelayMs = 5000)
    static class AnnotatedHandler implements TaskHandler {
        public String getTaskType() { return "TYPE_A"; }
        public void execute(TaskInstance task) { }
    }

    private TaskInstance leasedTask(Long id, int executeCount, int maxRetryCount) {
        return TaskInstance.builder()
                .id(id)
                .taskType("TYPE_A")
                .bizId("BIZ-" + id)
                .executeCount(executeCount)
                .maxRetryCount(maxRetryCount)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(1000L)
                .workerId("worker-" + id)
                .lockedAt(LocalDateTime.now().minusSeconds(10))
                .lockExpireAt(LocalDateTime.now().plusMinutes(5))
                .version(2)
                .traceId("trace-" + id)
                .build();
    }
}
