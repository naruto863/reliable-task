package com.reliabletask.core.event;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("TaskEventPublisher 测试")
class TaskEventPublisherTest {

    @Test
    @DisplayName("publish - 多监听器按顺序调用且异常隔离")
    void publish_multipleListeners_invokesInOrderAndIsolatesFailure() {
        List<String> calls = new ArrayList<>();
        TaskEvent event = TaskEvent.builder()
                .eventType(TaskEventType.SUBMITTED)
                .taskId(1L)
                .statusAfter(TaskStatus.PENDING)
                .build();
        TaskEventPublisher publisher = new TaskEventPublisher(List.of(
                ignored -> calls.add("first"),
                ignored -> {
                    calls.add("throwing");
                    throw new IllegalStateException("listener failed");
                },
                ignored -> calls.add("third")
        ));

        assertDoesNotThrow(() -> publisher.publish(event));

        assertEquals(List.of("first", "throwing", "third"), calls);
    }

    @Test
    @DisplayName("publish - 空监听器和 null 事件均不抛异常")
    void publish_emptyListenersAndNullEvent_noop() {
        TaskEventPublisher publisher = new TaskEventPublisher(List.of());

        assertDoesNotThrow(() -> publisher.publish(null));
    }
}
