package com.reliabletask.executor.handler;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.exception.RetryableException;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.executor.interceptor.TaskExecutionInterceptor;
import com.reliabletask.executor.retry.RetryEngine;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.executor.worker.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TaskExecutor 测试")
@ExtendWith(MockitoExtension.class)
class TaskExecutorTest {

    @Mock
    private TaskStore taskStore;

    @Mock(lenient = true)
    private TaskExecutorFactory executorFactory;

    private TaskHandlerRegistry registry;
    private TaskExecutor executor;
    private RetryEngine retryEngine;

    @BeforeEach
    void setUp() {
        registry = new TaskHandlerRegistry();
        ExecutorService realPool = Executors.newCachedThreadPool();
        when(executorFactory.getExecutor(anyString())).thenReturn(realPool);
        retryEngine = new RetryEngine(taskStore);
        executor = new TaskExecutor(taskStore, registry, executorFactory, retryEngine,
                new TaskExecutionInterceptor());
    }

    @Test
    @DisplayName("execute - Handler 执行成功标记 SUCCESS")
    void execute_success_marksSuccess() {
        registry.registerHandler(new SuccessHandler());

        TaskInstance task = TaskInstance.builder()
                .id(1L).taskType("SUCCESS").bizId("BIZ-1")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .workerId("worker-1").traceId("trace-1")
                .build();

        executor.execute(task);

        verify(taskStore).markSuccess(1L);
        verify(taskStore).saveLog(eq(1L), eq(1), eq("RUNNING"), eq("SUCCESS"),
                eq(true), anyLong(), isNull(), isNull(), eq("worker-1"), eq("trace-1"));
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("execute - 成功执行记录指标事件")
    void execute_success_recordsMetricsEvent() {
        TaskMetricsRecorder metricsRecorder = mock(TaskMetricsRecorder.class);
        executor = new TaskExecutor(taskStore, registry, executorFactory, retryEngine,
                new TaskExecutionInterceptor(), new com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer(),
                new WorkerProperties(), metricsRecorder, null);
        registry.registerHandler(new SuccessHandler());

        TaskInstance task = TaskInstance.builder()
                .id(15L).taskType("SUCCESS").bizId("BIZ-15")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .workerId("worker-15").traceId("trace-15")
                .build();

        executor.execute(task);

        verify(metricsRecorder).record(argThat(event ->
                event.getTaskId().equals(15L)
                        && event.getStatus() == TaskStatus.SUCCESS
                        && event.isSuccess()
                        && "trace-15".equals(event.getTraceId())));
    }

    @Test
    @DisplayName("execute - traceId 传播到 Handler 线程并执行后清理")
    void execute_traceIdPropagatesToHandlerThreadAndClears() {
        registry.registerHandler(new TraceAssertingHandler("trace-assert"));

        TaskInstance task = TaskInstance.builder()
                .id(11L).taskType("TRACE_ASSERT").bizId("BIZ-11")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .workerId("worker-11").traceId("trace-assert")
                .build();

        executor.execute(task);

        verify(taskStore).markSuccess(11L);
        verify(taskStore).saveLog(eq(11L), eq(1), eq("RUNNING"), eq("SUCCESS"),
                eq(true), anyLong(), isNull(), isNull(), eq("worker-11"), eq("trace-assert"));
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("execute - 类型化 payload 反序列化后传给 Handler")
    void execute_typedPayload_deserializesBeforeHandler() {
        TypedPayloadHandler handler = new TypedPayloadHandler();
        registry.registerHandler(handler);

        TaskInstance task = TaskInstance.builder()
                .id(12L).taskType("TYPED_PAYLOAD").bizId("BIZ-12")
                .payload("{\"orderId\":\"ORD-12\"}")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .build();

        executor.execute(task);

        verify(taskStore).markSuccess(12L);
        assertEquals("ORD-12", handler.received.get().orderId());
    }

    @Test
    @DisplayName("execute - payload 反序列化失败进入 DEAD")
    void execute_payloadDeserializeFailure_marksDead() {
        registry.registerHandler(new TypedPayloadHandler());

        TaskInstance task = TaskInstance.builder()
                .id(13L).taskType("TYPED_PAYLOAD").bizId("BIZ-13")
                .payload("{invalid-json")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(0)
                .build();

        executor.execute(task);

        verify(taskStore).markDead(eq(13L), eq("IllegalArgumentException"), anyString());
        verify(taskStore).saveLog(eq(13L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), anyLong(), eq("IllegalArgumentException"), anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("execute - 心跳开启时运行期间续约任务锁")
    void execute_heartbeatEnabled_renewsTaskLease() {
        WorkerProperties workerProperties = new WorkerProperties();
        workerProperties.setHeartbeatEnabled(true);
        workerProperties.setHeartbeatIntervalMs(10L);
        workerProperties.setLockRenewalTtlSeconds(30L);
        executor = new TaskExecutor(taskStore, registry, executorFactory, retryEngine,
                new TaskExecutionInterceptor(), new com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer(),
                workerProperties);
        registry.registerHandler(new ShortRunningHandler());

        TaskInstance task = TaskInstance.builder()
                .id(14L).taskType("SHORT_RUNNING").bizId("BIZ-14")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .workerId("worker-14")
                .build();

        executor.execute(task);

        verify(taskStore, atLeastOnce()).renewTaskLease(eq(14L), eq("worker-14"), any(), any());
        verify(taskStore).markSuccess(14L);
    }

    @Test
    @DisplayName("execute - 可重试异常标记 RETRYING")
    void execute_retryableException_marksRetrying() {
        registry.registerHandler(new RetryableExceptionHandler());

        TaskInstance task = TaskInstance.builder()
                .id(2L).taskType("RETRYABLE").bizId("BIZ-2")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        executor.execute(task);

        verify(taskStore).markWaitRetry(eq(2L), anyString(), anyString(), any());
        verify(taskStore).saveLog(eq(2L), eq(1), eq("RUNNING"), eq("RETRYING"),
                eq(false), anyLong(), anyString(), anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("execute - 不可重试异常标记 DEAD")
    void execute_nonRetryableException_marksDead() {
        registry.registerHandler(new NonRetryableExceptionHandler());

        TaskInstance task = TaskInstance.builder()
                .id(3L).taskType("NON_RETRYABLE").bizId("BIZ-3")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        executor.execute(task);

        verify(taskStore).markDead(eq(3L), anyString(), anyString());
        verify(taskStore).saveLog(eq(3L), eq(1), eq("RUNNING"), eq("DEAD"),
                eq(false), anyLong(), anyString(), anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("execute - 超过最大重试次数标记 DEAD")
    void execute_retriesExhausted_marksDead() {
        registry.registerHandler(new RetryableExceptionHandler());

        TaskInstance task = TaskInstance.builder()
                .id(4L).taskType("RETRYABLE").bizId("BIZ-4")
                .status(TaskStatus.RUNNING).executeCount(4).maxRetryCount(3)
                .build();

        executor.execute(task);

        verify(taskStore).markDead(eq(4L), anyString(), anyString());
    }

    @Test
    @DisplayName("execute - 无 Handler 标记 DEAD")
    void execute_noHandler_marksDead() {
        TaskInstance task = TaskInstance.builder()
                .id(5L).taskType("UNKNOWN").bizId("BIZ-5")
                .status(TaskStatus.RUNNING).executeCount(0)
                .workerId("worker-5").traceId("trace-5")
                .build();

        executor.execute(task);

        verify(taskStore).markDead(eq(5L), eq("NO_HANDLER"), anyString());
        verify(taskStore).saveLog(eq(5L), eq(0), eq("RUNNING"), eq("DEAD"),
                eq(false), anyLong(), eq("NO_HANDLER"), anyString(), eq("worker-5"), eq("trace-5"));
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("execute - 超时异常标记 RETRYING")
    void execute_timeout_marksRetrying() {
        registry.registerHandler(new SlowHandler());

        TaskInstance task = TaskInstance.builder()
                .id(6L).taskType("SLOW").bizId("BIZ-6")
                .status(TaskStatus.RUNNING).executeCount(1).maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED).retryIntervalMs(1000L)
                .build();

        executor.execute(task);

        verify(taskStore).markWaitRetry(eq(6L), anyString(), anyString(), any());
    }

    // ==================== Test Handlers ====================

    static class SuccessHandler implements TaskHandler {
        public String getTaskType() { return "SUCCESS"; }
        public void execute(TaskInstance task) { }
        public long timeoutMs() { return 5000L; }
    }

    static class TraceAssertingHandler implements TaskHandler {
        private final String expectedTraceId;

        TraceAssertingHandler(String expectedTraceId) {
            this.expectedTraceId = expectedTraceId;
        }

        public String getTaskType() { return "TRACE_ASSERT"; }
        public void execute(TaskInstance task) {
            assertEquals(expectedTraceId, TraceContext.getTraceId());
        }
        public long timeoutMs() { return 5000L; }
    }

    static class TypedPayloadHandler implements TaskHandler {
        private final AtomicReference<OrderPayload> received = new AtomicReference<>();

        public String getTaskType() { return "TYPED_PAYLOAD"; }
        public void execute(TaskInstance task) { }
        public Class<?> payloadType() { return OrderPayload.class; }
        public void execute(TaskInstance task, Object payload) {
            received.set((OrderPayload) payload);
        }
        public long timeoutMs() { return 5000L; }
    }

    record OrderPayload(String orderId) {
    }

    static class RetryableExceptionHandler implements TaskHandler {
        public String getTaskType() { return "RETRYABLE"; }
        public void execute(TaskInstance task) throws Exception {
            throw new RetryableException("temporary failure");
        }
        public long timeoutMs() { return 5000L; }
    }

    static class NonRetryableExceptionHandler implements TaskHandler {
        public String getTaskType() { return "NON_RETRYABLE"; }
        public void execute(TaskInstance task) throws Exception {
            throw new NonRetryableException("invalid data");
        }
        public long timeoutMs() { return 5000L; }
    }

    static class SlowHandler implements TaskHandler {
        public String getTaskType() { return "SLOW"; }
        public void execute(TaskInstance task) throws Exception {
            Thread.sleep(10000);
        }
        public long timeoutMs() { return 100L; }
    }

    static class ShortRunningHandler implements TaskHandler {
        public String getTaskType() { return "SHORT_RUNNING"; }
        public void execute(TaskInstance task) throws Exception {
            Thread.sleep(50);
        }
        public long timeoutMs() { return 5000L; }
    }
}
