package com.reliabletask.core.deadletter;

import com.reliabletask.core.model.DeadLetterContext;
import com.reliabletask.core.spi.TaskDeadLetterHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务死信处理分发器。
 *
 * <p>按注入顺序调用多个处理器，并隔离单个处理器异常。
 */
@Slf4j
public class TaskDeadLetterDispatcher {

    private final List<TaskDeadLetterHandler> handlers;

    public TaskDeadLetterDispatcher() {
        this(List.of());
    }

    public TaskDeadLetterDispatcher(List<TaskDeadLetterHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            this.handlers = List.of();
            return;
        }
        List<TaskDeadLetterHandler> filtered = new ArrayList<>();
        for (TaskDeadLetterHandler handler : handlers) {
            if (handler != null) {
                filtered.add(handler);
            }
        }
        this.handlers = List.copyOf(filtered);
    }

    /**
     * 分发死信上下文。
     *
     * @param context 死信上下文
     */
    public void dispatch(DeadLetterContext context) {
        if (context == null || handlers.isEmpty()) {
            return;
        }
        DeadLetterContext effectiveContext = context.getDeadAt() == null
                ? context.toBuilder().deadAt(LocalDateTime.now()).build()
                : context;
        Long taskId = effectiveContext.getTask() == null ? null : effectiveContext.getTask().getId();
        for (TaskDeadLetterHandler handler : handlers) {
            try {
                handler.handle(effectiveContext);
            } catch (RuntimeException e) {
                log.warn("Task dead letter handler failed: handler={}, taskId={}, source={}, reason={}",
                        handler.getName(), taskId, effectiveContext.getSource(), e.getMessage());
            }
        }
    }
}
