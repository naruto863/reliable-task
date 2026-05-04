package com.reliabletask.executor.alert;

import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;

/**
 * 任务告警触发服务。
 */
public interface TaskAlertService {

    void notifyDead(TaskInstance task, String reason);

    void recordMetricsEvent(TaskExecutionMetricsEvent event);

    void notifyPendingBacklog(long pendingCount, long threshold);
}
