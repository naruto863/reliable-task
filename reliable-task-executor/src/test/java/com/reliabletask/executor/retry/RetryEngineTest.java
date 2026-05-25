package com.reliabletask.executor.retry;

import com.reliabletask.core.annotation.TaskRetryable;
import com.reliabletask.core.diagnostics.TaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskFailureDiagnostic;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.exception.RetryableException;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.executor.alert.TaskAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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
                eq(false), eq(200L), eq("REMOTE_TIMEOUT"), eq("masked summary"), isNull(), isNull());
        verify(metricsRecorder).record(argThat(event ->
                "REMOTE_TIMEOUT".equals(event.getErrorCode())));
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

    @TaskRetryable(maxRetryCount = 2, retryIntervalMs = 500, maxDelayMs = 5000)
    static class AnnotatedHandler implements TaskHandler {
        public String getTaskType() { return "TYPE_A"; }
        public void execute(TaskInstance task) { }
    }
}
