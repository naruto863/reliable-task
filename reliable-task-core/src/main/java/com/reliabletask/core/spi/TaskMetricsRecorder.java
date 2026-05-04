package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskExecutionMetricsEvent;

/**
 * 任务执行指标记录 SPI。
 */
public interface TaskMetricsRecorder {

    /**
     * 记录任务执行指标事件。
     *
     * @param event 指标事件
     */
    void record(TaskExecutionMetricsEvent event);
}
