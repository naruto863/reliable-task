package com.reliabletask.executor.recovery;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TaskRecoveryScheduler 测试")
@ExtendWith(MockitoExtension.class)
class TaskRecoverySchedulerTest {

    @Mock
    private TaskStore taskStore;

    private RecoveryProperties properties;
    private TaskRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new RecoveryProperties();
        properties.setEnabled(true);
        properties.setIntervalMs(30000L);
        properties.setTimeoutSeconds(300L);
        properties.setMaxResetPerScan(100);

        scheduler = new TaskRecoveryScheduler(taskStore, properties);
    }

    @Test
    @DisplayName("scanAndRecover - 开关关闭跳过执行")
    void scanAndRecover_disabled_skipsExecution() {
        properties.setEnabled(false);

        scheduler.scanAndRecover();

        verify(taskStore, never()).findTimeoutTasks(any());
    }

    @Test
    @DisplayName("scanAndRecover - 无超时任务不调用 reset")
    void scanAndRecover_noTimeoutTasks_noReset() {
        when(taskStore.findTimeoutTasks(any())).thenReturn(List.of());

        scheduler.scanAndRecover();

        verify(taskStore).findTimeoutTasks(any());
        verify(taskStore, never()).resetTimeoutTask(any());
    }

    @Test
    @DisplayName("scanAndRecover - 超时任务被重置")
    void scanAndRecover_timeoutTasks_areReset() {
        TaskInstance task1 = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .updateTime(LocalDateTime.now().minusMinutes(10))
                .status(TaskStatus.RUNNING)
                .build();

        TaskInstance task2 = TaskInstance.builder()
                .id(2L)
                .taskType("TYPE_B")
                .bizId("BIZ-2")
                .updateTime(LocalDateTime.now().minusMinutes(15))
                .status(TaskStatus.RUNNING)
                .build();

        when(taskStore.findTimeoutTasks(any())).thenReturn(List.of(task1, task2));
        when(taskStore.resetTimeoutTask(1L)).thenReturn(true);
        when(taskStore.resetTimeoutTask(2L)).thenReturn(true);

        scheduler.scanAndRecover();

        verify(taskStore).resetTimeoutTask(1L);
        verify(taskStore).resetTimeoutTask(2L);
    }

    @Test
    @DisplayName("scanAndRecover - 达到最大重置数量停止")
    void scanAndRecover_exceedsMaxReset_stopsAtLimit() {
        properties.setMaxResetPerScan(2);

        TaskInstance t1 = TaskInstance.builder().id(1L).status(TaskStatus.RUNNING).build();
        TaskInstance t2 = TaskInstance.builder().id(2L).status(TaskStatus.RUNNING).build();
        TaskInstance t3 = TaskInstance.builder().id(3L).status(TaskStatus.RUNNING).build();

        when(taskStore.findTimeoutTasks(any())).thenReturn(List.of(t1, t2, t3));
        when(taskStore.resetTimeoutTask(anyLong())).thenReturn(true);

        scheduler.scanAndRecover();

        verify(taskStore).resetTimeoutTask(1L);
        verify(taskStore).resetTimeoutTask(2L);
        verify(taskStore, never()).resetTimeoutTask(3L);
    }

    @Test
    @DisplayName("scanAndRecover - 重置失败的任务不计入数量")
    void scanAndRecover_resetFailure_continuesToNext() {
        properties.setMaxResetPerScan(2);

        TaskInstance t1 = TaskInstance.builder().id(1L).status(TaskStatus.RUNNING).build();
        TaskInstance t2 = TaskInstance.builder().id(2L).status(TaskStatus.RUNNING).build();
        TaskInstance t3 = TaskInstance.builder().id(3L).status(TaskStatus.RUNNING).build();

        when(taskStore.findTimeoutTasks(any())).thenReturn(List.of(t1, t2, t3));
        when(taskStore.resetTimeoutTask(1L)).thenReturn(false);
        when(taskStore.resetTimeoutTask(2L)).thenReturn(true);
        when(taskStore.resetTimeoutTask(3L)).thenReturn(true);

        scheduler.scanAndRecover();

        verify(taskStore).resetTimeoutTask(1L);
        verify(taskStore).resetTimeoutTask(2L);
        verify(taskStore).resetTimeoutTask(3L);
    }

    @Test
    @DisplayName("scanAndRecover - 使用当前时间扫描已过期锁")
    void scanAndRecover_usesCurrentTimeForExpiredLockScan() {
        properties.setTimeoutSeconds(600L);

        when(taskStore.findTimeoutTasks(any())).thenReturn(List.of());

        scheduler.scanAndRecover();

        verify(taskStore).findTimeoutTasks(argThat(threshold -> {
            LocalDateTime expected = LocalDateTime.now();
            return threshold.isAfter(expected.minusSeconds(1))
                    && threshold.isBefore(expected.plusSeconds(1));
        }));
    }
}
