package com.reliabletask.executor.interceptor;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("TaskExecutionInterceptor 测试")
class TaskExecutionInterceptorTest {

    private final TaskExecutionInterceptor interceptor = new TaskExecutionInterceptor();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("beforeExecute - 设置任务 traceId 到 MDC")
    void beforeExecute_setsTraceId() {
        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .traceId("trace-001")
                .build();

        interceptor.beforeExecute(task);

        assertEquals("trace-001", TraceContext.getTraceId());
    }

    @Test
    @DisplayName("beforeExecute - 无 traceId 时清理已有 MDC")
    void beforeExecute_withoutTraceId_clearsExistingTraceId() {
        TraceContext.setTraceId("stale-trace");
        TaskInstance task = TaskInstance.builder()
                .id(2L)
                .build();

        interceptor.beforeExecute(task);

        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("afterExecute - 清理 MDC")
    void afterExecute_clearsTraceId() {
        TraceContext.setTraceId("trace-002");

        interceptor.afterExecute();

        assertNull(TraceContext.getTraceId());
    }
}
