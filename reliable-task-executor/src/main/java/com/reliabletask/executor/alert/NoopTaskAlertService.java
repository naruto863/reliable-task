package com.reliabletask.executor.alert;

import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;

/**
 * 默认告警服务：忽略告警事件。
 */
public class NoopTaskAlertService implements TaskAlertService {

    @Override
    public void notifyDead(TaskInstance task, String reason) {
        // no-op
    }

    @Override
    public void recordMetricsEvent(TaskExecutionMetricsEvent event) {
        // no-op
    }

    @Override
    public void notifyPendingBacklog(long pendingCount, long threshold) {
        // no-op
    }
}
