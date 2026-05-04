package com.reliabletask.executor.alert;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.AlarmNotifier;
import com.reliabletask.core.spi.noop.NoopAlarmNotifier;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 默认告警服务，负责将内部触发点转换为 AlarmNotifier 调用。
 */
@Slf4j
public class DefaultTaskAlertService implements TaskAlertService {

    private final AlarmNotifier alarmNotifier;
    private final AlertProperties properties;
    private final Deque<ExecutionPoint> executionWindow = new ArrayDeque<>();

    public DefaultTaskAlertService(AlarmNotifier alarmNotifier, AlertProperties properties) {
        this.alarmNotifier = alarmNotifier != null ? alarmNotifier : new NoopAlarmNotifier();
        this.properties = properties != null ? properties : new AlertProperties();
    }

    @Override
    public void notifyDead(TaskInstance task, String reason) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            alarmNotifier.notify(task, reason);
        } catch (RuntimeException e) {
            log.warn("Failed to send dead task alarm: taskId={}, notifier={}, reason={}",
                    task == null ? null : task.getId(), alarmNotifier.getName(), e.getMessage());
        }
    }

    @Override
    public synchronized void recordMetricsEvent(TaskExecutionMetricsEvent event) {
        if (!properties.isEnabled() || event == null || event.getStatus() == TaskStatus.PENDING) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        executionWindow.addLast(new ExecutionPoint(now, event.isSuccess()));
        prune(now);

        long total = executionWindow.size();
        if (total == 0 || properties.getFailureRateThreshold() <= 0) {
            return;
        }

        long failed = executionWindow.stream().filter(point -> !point.success()).count();
        double failureRate = failed * 1.0D / total;
        if (failureRate >= properties.getFailureRateThreshold()) {
            safeNotify("FAILURE_RATE",
                    "failureRate=" + failureRate + ", failed=" + failed + ", total=" + total
                            + ", windowSeconds=" + properties.getWindowSeconds());
        }
    }

    @Override
    public void notifyPendingBacklog(long pendingCount, long threshold) {
        if (!properties.isEnabled()) {
            return;
        }
        safeNotify("PENDING_BACKLOG",
                "pendingTasks=" + pendingCount + ", threshold=" + threshold);
    }

    private void safeNotify(String alarmType, String reason) {
        try {
            alarmNotifier.notify(alarmType, reason);
        } catch (RuntimeException e) {
            log.warn("Failed to send system alarm: type={}, notifier={}, reason={}",
                    alarmType, alarmNotifier.getName(), e.getMessage());
        }
    }

    private void prune(LocalDateTime now) {
        LocalDateTime threshold = now.minusSeconds(Math.max(properties.getWindowSeconds(), 1L));
        while (!executionWindow.isEmpty() && executionWindow.peekFirst().time().isBefore(threshold)) {
            executionWindow.removeFirst();
        }
    }

    private record ExecutionPoint(LocalDateTime time, boolean success) {
    }
}
