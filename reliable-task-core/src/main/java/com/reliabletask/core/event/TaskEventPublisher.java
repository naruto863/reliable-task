package com.reliabletask.core.event;

import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.spi.TaskEventListener;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步任务事件发布器。
 *
 * <p>按注入顺序调用多个监听器，并隔离单个监听器异常。
 */
@Slf4j
public class TaskEventPublisher {

    private final List<TaskEventListener> listeners;

    public TaskEventPublisher() {
        this(List.of());
    }

    public TaskEventPublisher(List<TaskEventListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            this.listeners = List.of();
            return;
        }
        List<TaskEventListener> filtered = new ArrayList<>();
        for (TaskEventListener listener : listeners) {
            if (listener != null) {
                filtered.add(listener);
            }
        }
        this.listeners = List.copyOf(filtered);
    }

    public void publish(TaskEvent event) {
        if (event == null || listeners.isEmpty()) {
            return;
        }
        TaskEvent effectiveEvent = event.getEventTime() == null
                ? event.toBuilder().eventTime(LocalDateTime.now()).build()
                : event;
        for (TaskEventListener listener : listeners) {
            try {
                listener.onEvent(effectiveEvent);
            } catch (RuntimeException e) {
                log.warn("Task event listener failed: listener={}, eventType={}, taskId={}, reason={}",
                        listener.getClass().getName(), effectiveEvent.getEventType(),
                        effectiveEvent.getTaskId(), e.getMessage());
            }
        }
    }
}
