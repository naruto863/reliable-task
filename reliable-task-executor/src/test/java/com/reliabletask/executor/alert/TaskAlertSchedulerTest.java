package com.reliabletask.executor.alert;

import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.vo.TaskStatsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

@DisplayName("TaskAlertScheduler 测试")
class TaskAlertSchedulerTest {

    @Test
    @DisplayName("scanPendingBacklog - pending 超阈值触发告警服务")
    void scanPendingBacklog_thresholdExceeded_notifies() {
        TaskStore taskStore = mock(TaskStore.class);
        TaskAlertService alertService = mock(TaskAlertService.class);
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        properties.setPendingThreshold(100L);
        TaskStatsVO stats = new TaskStatsVO();
        stats.setPendingTasks(101L);
        when(taskStore.getStats()).thenReturn(stats);

        new TaskAlertScheduler(taskStore, properties, alertService).scanPendingBacklog();

        verify(alertService).notifyPendingBacklog(101L, 100L);
    }

    @Test
    @DisplayName("scanPendingBacklog - 告警关闭时不查询 Store")
    void scanPendingBacklog_disabled_doesNotQueryStore() {
        TaskStore taskStore = mock(TaskStore.class);
        TaskAlertService alertService = mock(TaskAlertService.class);
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(false);
        properties.setPendingThreshold(100L);

        new TaskAlertScheduler(taskStore, properties, alertService).scanPendingBacklog();

        verifyNoInteractions(taskStore, alertService);
    }
}
