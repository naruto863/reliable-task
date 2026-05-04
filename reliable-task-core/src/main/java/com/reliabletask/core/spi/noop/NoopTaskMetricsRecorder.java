package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskMetricsRecorder;

/**
 * 默认指标实现：忽略指标事件。
 */
public class NoopTaskMetricsRecorder implements TaskMetricsRecorder {

    @Override
    public void record(TaskExecutionMetricsEvent event) {
        // no-op
    }
}
