package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskMetricsRecorder;

/**
 * 默认指标实现：忽略指标事件。
 *
 * <p>用于没有 Micrometer 或用户未启用 metrics 的场景。任务执行链路仍会发布事件，
 * 但不会产生计数器、Timer 或 Gauge。
 */
public class NoopTaskMetricsRecorder implements TaskMetricsRecorder {

    @Override
    public void record(TaskExecutionMetricsEvent event) {
        // no-op
    }
}
