package com.reliabletask.executor.interceptor;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("TaskInterceptorChain 测试")
class TaskInterceptorChainTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("before 按 order 升序执行，after 按逆序执行")
    void execute_ordersBeforeAscendingAndAfterDescending() {
        List<String> events = new ArrayList<>();
        TaskInterceptorChain chain = TaskInterceptorChain.of(List.of(
                new RecordingInterceptor("second", 20, events),
                new RecordingInterceptor("first", 10, events)
        ));
        TaskExecutionContext context = TaskExecutionContext.from(TaskInstance.builder().id(1L).build());

        chain.beforeExecute(context);
        chain.afterExecute(context);

        assertEquals(List.of("before:first", "before:second", "after:second", "after:first"), events);
    }

    @Test
    @DisplayName("onError 按逆序执行并传递原始异常")
    void onError_ordersDescendingAndKeepsOriginalError() {
        List<String> events = new ArrayList<>();
        AtomicReference<Throwable> seenError = new AtomicReference<>();
        RuntimeException original = new RuntimeException("handler failed");
        TaskInterceptorChain chain = TaskInterceptorChain.of(List.of(
                new RecordingInterceptor("first", 10, events),
                new RecordingInterceptor("second", 20, events, seenError)
        ));

        chain.onError(TaskExecutionContext.from(TaskInstance.builder().id(2L).build()), original);

        assertEquals(List.of("error:second", "error:first"), events);
        assertSame(original, seenError.get());
    }

    @Test
    @DisplayName("interceptor 自身异常被隔离，不覆盖 Handler 原始异常")
    void interceptorExceptions_areIsolated() {
        List<String> events = new ArrayList<>();
        AtomicReference<Throwable> seenError = new AtomicReference<>();
        RuntimeException original = new RuntimeException("handler failed");
        TaskInterceptorChain chain = TaskInterceptorChain.of(List.of(
                new ThrowingInterceptor(10),
                new RecordingInterceptor("observer", 20, events, seenError)
        ));
        TaskExecutionContext context = TaskExecutionContext.from(TaskInstance.builder().id(3L).build());

        assertDoesNotThrow(() -> chain.beforeExecute(context));
        assertDoesNotThrow(() -> chain.onError(context, original));
        assertDoesNotThrow(() -> chain.afterExecute(context));

        assertEquals(List.of("before:observer", "error:observer", "after:observer"), events);
        assertSame(original, seenError.get());
    }

    @Test
    @DisplayName("默认 trace interceptor 设置并清理 TraceContext")
    void traceInterceptor_setsAndClearsTraceContext() {
        TaskInterceptorChain chain = TaskInterceptorChain.of(List.of(new TraceTaskInterceptor()));
        TaskExecutionContext context = TaskExecutionContext.from(TaskInstance.builder()
                .id(4L)
                .traceId("trace-chain")
                .build());

        chain.beforeExecute(context);
        assertEquals("trace-chain", TraceContext.getTraceId());

        chain.afterExecute(context);
        assertNull(TraceContext.getTraceId());
    }

    private record RecordingInterceptor(String name, int order, List<String> events,
                                        AtomicReference<Throwable> seenError) implements TaskInterceptor {

        private RecordingInterceptor(String name, int order, List<String> events) {
            this(name, order, events, new AtomicReference<>());
        }

        @Override
        public void beforeExecute(TaskExecutionContext context) {
            events.add("before:" + name);
        }

        @Override
        public void afterExecute(TaskExecutionContext context) {
            events.add("after:" + name);
        }

        @Override
        public void onError(TaskExecutionContext context, Throwable error) {
            events.add("error:" + name);
            seenError.set(error);
        }
    }

    private record ThrowingInterceptor(int order) implements TaskInterceptor {

        @Override
        public void beforeExecute(TaskExecutionContext context) {
            throw new IllegalStateException("before failed");
        }

        @Override
        public void afterExecute(TaskExecutionContext context) {
            throw new IllegalStateException("after failed");
        }

        @Override
        public void onError(TaskExecutionContext context, Throwable error) {
            throw new IllegalStateException("error failed");
        }
    }
}
