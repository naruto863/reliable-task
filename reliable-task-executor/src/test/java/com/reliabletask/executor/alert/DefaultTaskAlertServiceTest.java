package com.reliabletask.executor.alert;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.AlarmNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("DefaultTaskAlertService 测试")
class DefaultTaskAlertServiceTest {

    @Test
    @DisplayName("notifyDead - DEAD 任务触发 AlarmNotifier")
    void notifyDead_sendsAlarm() {
        AlarmNotifier notifier = mock(AlarmNotifier.class);
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        DefaultTaskAlertService service = new DefaultTaskAlertService(notifier, properties);
        TaskInstance task = TaskInstance.builder().id(1L).taskType("TYPE_A").build();

        service.notifyDead(task, "max retry exceeded");

        verify(notifier).notify(eq(task), contains("max retry exceeded"));
    }

    @Test
    @DisplayName("notifyPendingBacklog - pending 超阈值触发系统告警")
    void notifyPendingBacklog_sendsSystemAlarm() {
        AlarmNotifier notifier = mock(AlarmNotifier.class);
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        DefaultTaskAlertService service = new DefaultTaskAlertService(notifier, properties);

        service.notifyPendingBacklog(101L, 100L);

        verify(notifier).notify(eq("PENDING_BACKLOG"), contains("pendingTasks=101"));
    }

    @Test
    @DisplayName("recordMetricsEvent - 失败率超过阈值触发系统告警")
    void recordMetricsEvent_failureRateExceeded_sendsSystemAlarm() {
        AlarmNotifier notifier = mock(AlarmNotifier.class);
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        properties.setFailureRateThreshold(0.5D);
        properties.setWindowSeconds(60L);
        DefaultTaskAlertService service = new DefaultTaskAlertService(notifier, properties);

        service.recordMetricsEvent(TaskExecutionMetricsEvent.builder()
                .status(TaskStatus.SUCCESS)
                .success(true)
                .build());
        service.recordMetricsEvent(TaskExecutionMetricsEvent.builder()
                .status(TaskStatus.DEAD)
                .success(false)
                .build());

        verify(notifier).notify(eq("FAILURE_RATE"), contains("failed=1"));
    }

    @Test
    @DisplayName("notifyDead - notifier 异常被隔离")
    void notifyDead_notifierThrows_isIsolated() {
        AlarmNotifier notifier = mock(AlarmNotifier.class);
        doThrow(new RuntimeException("boom")).when(notifier).notify(any(TaskInstance.class), anyString());
        when(notifier.getName()).thenReturn("throwing");
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        DefaultTaskAlertService service = new DefaultTaskAlertService(notifier, properties);

        service.notifyDead(TaskInstance.builder().id(1L).build(), "dead");

        verify(notifier).notify(any(TaskInstance.class), anyString());
    }
}
