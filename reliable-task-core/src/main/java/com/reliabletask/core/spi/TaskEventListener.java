package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskEvent;

/**
 * 任务状态事件监听器。
 *
 * <p>监听器应保持轻量；发布器会同步调用并隔离单个监听器异常。
 */
@FunctionalInterface
public interface TaskEventListener {

    /**
     * 处理任务状态事件。
     *
     * @param event 任务事件
     */
    void onEvent(TaskEvent event);
}
