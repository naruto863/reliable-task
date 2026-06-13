package com.reliabletask.core.deadletter;

import com.reliabletask.core.model.DeadLetterContext;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.noop.NoopTaskDeadLetterHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("TaskDeadLetterDispatcher 测试")
class TaskDeadLetterDispatcherTest {

    @Test
    @DisplayName("dispatch - 多处理器按顺序调用且异常隔离")
    void dispatch_multipleHandlers_invokesInOrderAndIsolatesFailure() {
        List<String> calls = new ArrayList<>();
        DeadLetterContext context = DeadLetterContext.builder()
                .task(TaskInstance.builder().id(1L).build())
                .source("RetryEngine")
                .reason("retry exhausted")
                .build();
        TaskDeadLetterDispatcher dispatcher = new TaskDeadLetterDispatcher(List.of(
                deadLetter -> {
                    assertNotNull(deadLetter.getDeadAt());
                    calls.add("first");
                },
                deadLetter -> {
                    calls.add("throwing");
                    throw new IllegalStateException("handler failed");
                },
                deadLetter -> calls.add("third")
        ));

        assertDoesNotThrow(() -> dispatcher.dispatch(context));

        assertEquals(List.of("first", "throwing", "third"), calls);
    }

    @Test
    @DisplayName("dispatch - 空处理器和 null 上下文均不抛异常")
    void dispatch_emptyHandlersAndNullContext_noop() {
        TaskDeadLetterDispatcher dispatcher = new TaskDeadLetterDispatcher(List.of());

        assertDoesNotThrow(() -> dispatcher.dispatch(null));
    }

    @Test
    @DisplayName("NoopTaskDeadLetterHandler - 默认实现不抛异常")
    void noopTaskDeadLetterHandler_noop() {
        NoopTaskDeadLetterHandler handler = new NoopTaskDeadLetterHandler();

        assertDoesNotThrow(() -> handler.handle(DeadLetterContext.builder().build()));
    }
}
