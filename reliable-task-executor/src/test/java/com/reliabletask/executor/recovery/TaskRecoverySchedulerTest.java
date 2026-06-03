package com.reliabletask.executor.recovery;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

        verify(taskStore, never()).findTimeoutTasks(any(LocalDateTime.class), anyInt());
    }

    @Test
    @DisplayName("scanAndRecover - 无超时任务不调用 reset")
    void scanAndRecover_noTimeoutTasks_noReset() {
        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(100))).thenReturn(List.of());

        scheduler.scanAndRecover();

        verify(taskStore).findTimeoutTasks(any(LocalDateTime.class), eq(100));
        verify(taskStore, never()).resetTimeoutTask(any(TaskExecutionLease.class));
    }

    @Test
    @DisplayName("scanAndRecover - store 返回 null 时按空列表处理")
    void scanAndRecover_nullTimeoutTasks_noReset() {
        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(100))).thenReturn(null);

        assertDoesNotThrow(() -> scheduler.scanAndRecover());

        verify(taskStore).findTimeoutTasks(any(LocalDateTime.class), eq(100));
        verify(taskStore, never()).resetTimeoutTask(any(TaskExecutionLease.class));
    }

    @Test
    @DisplayName("scanAndRecover - 超时任务被重置")
    void scanAndRecover_timeoutTasks_areReset() {
        TaskInstance task1 = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .updateTime(LocalDateTime.now().minusMinutes(10))
                .workerId("worker-1")
                .lockedAt(LocalDateTime.now().minusMinutes(10))
                .lockExpireAt(LocalDateTime.now().minusMinutes(5))
                .version(3)
                .status(TaskStatus.RUNNING)
                .build();

        TaskInstance task2 = TaskInstance.builder()
                .id(2L)
                .taskType("TYPE_B")
                .bizId("BIZ-2")
                .updateTime(LocalDateTime.now().minusMinutes(15))
                .workerId("worker-2")
                .lockedAt(LocalDateTime.now().minusMinutes(15))
                .lockExpireAt(LocalDateTime.now().minusMinutes(7))
                .version(4)
                .status(TaskStatus.RUNNING)
                .build();

        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(100))).thenReturn(List.of(task1, task2));
        when(taskStore.resetTimeoutTask(any(TaskExecutionLease.class))).thenReturn(true);

        scheduler.scanAndRecover();

        verify(taskStore).resetTimeoutTask(argThat((TaskExecutionLease lease) ->
                lease.getTaskId().equals(1L)
                        && "worker-1".equals(lease.getWorkerId())
                        && Integer.valueOf(3).equals(lease.getVersion())));
        verify(taskStore).resetTimeoutTask(argThat((TaskExecutionLease lease) ->
                lease.getTaskId().equals(2L)
                        && "worker-2".equals(lease.getWorkerId())
                        && Integer.valueOf(4).equals(lease.getVersion())));
    }

    @Test
    @DisplayName("scanAndRecover - 重置成功后发布 RECOVERED 事件")
    void scanAndRecover_resetSuccess_publishesRecoveredEvent() {
        List<TaskEvent> events = new ArrayList<>();
        scheduler = new TaskRecoveryScheduler(taskStore, properties,
                new TaskEventPublisher(List.of(events::add)));
        TaskInstance task = timeoutTask(11L);
        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(100))).thenReturn(List.of(task));
        when(taskStore.resetTimeoutTask(any(TaskExecutionLease.class))).thenReturn(true);

        scheduler.scanAndRecover();

        assertEquals(1, events.size());
        TaskEvent event = events.get(0);
        assertEquals(TaskEventType.RECOVERED, event.getEventType());
        assertEquals(TaskStatus.RUNNING, event.getStatusBefore());
        assertEquals(TaskStatus.PENDING, event.getStatusAfter());
        assertEquals("worker-11", event.getWorkerId());
    }

    @Test
    @DisplayName("scanAndRecover - 达到最大重置数量停止")
    void scanAndRecover_exceedsMaxReset_stopsAtLimit() {
        properties.setMaxResetPerScan(2);

        TaskInstance t1 = timeoutTask(1L);
        TaskInstance t2 = timeoutTask(2L);
        TaskInstance t3 = timeoutTask(3L);

        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(2))).thenReturn(List.of(t1, t2, t3));
        when(taskStore.resetTimeoutTask(any(TaskExecutionLease.class))).thenReturn(true);

        scheduler.scanAndRecover();

        verify(taskStore).findTimeoutTasks(any(LocalDateTime.class), eq(2));
        verify(taskStore, times(2)).resetTimeoutTask(any(TaskExecutionLease.class));
        verify(taskStore, never()).resetTimeoutTask(argThat((TaskExecutionLease lease) ->
                lease.getTaskId().equals(3L)));
    }

    @Test
    @DisplayName("scanAndRecover - 重置失败的任务不计入数量")
    void scanAndRecover_resetFailure_continuesToNext() {
        properties.setMaxResetPerScan(2);

        TaskInstance t1 = timeoutTask(1L);
        TaskInstance t2 = timeoutTask(2L);
        TaskInstance t3 = timeoutTask(3L);

        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(2))).thenReturn(List.of(t1, t2, t3));
        when(taskStore.resetTimeoutTask(any(TaskExecutionLease.class))).thenAnswer(invocation -> {
            TaskExecutionLease lease = invocation.getArgument(0);
            return !lease.getTaskId().equals(1L);
        });

        scheduler.scanAndRecover();

        verify(taskStore).resetTimeoutTask(argThat((TaskExecutionLease lease) -> lease.getTaskId().equals(1L)));
        verify(taskStore).resetTimeoutTask(argThat((TaskExecutionLease lease) -> lease.getTaskId().equals(2L)));
        verify(taskStore).resetTimeoutTask(argThat((TaskExecutionLease lease) -> lease.getTaskId().equals(3L)));
    }

    @Test
    @DisplayName("scanAndRecover - 使用当前时间作为锁过期扫描阈值")
    void scanAndRecover_usesNowForExpiredLockScan() {
        properties.setTimeoutSeconds(600L);

        when(taskStore.findTimeoutTasks(any(LocalDateTime.class), eq(100))).thenReturn(List.of());

        scheduler.scanAndRecover();

        verify(taskStore).findTimeoutTasks(argThat(threshold -> {
            LocalDateTime expected = LocalDateTime.now();
            return threshold.isAfter(expected.minusSeconds(1))
                    && threshold.isBefore(expected.plusSeconds(1));
        }), eq(100));
    }

    @Test
    @DisplayName("scanAndRecover - 最大重置数小于等于 0 时跳过扫描")
    void scanAndRecover_nonPositiveMaxReset_skipsScan() {
        properties.setMaxResetPerScan(0);

        scheduler.scanAndRecover();

        verify(taskStore, never()).findTimeoutTasks(any(LocalDateTime.class), anyInt());
    }

    private TaskInstance timeoutTask(Long id) {
        return TaskInstance.builder()
                .id(id)
                .taskType("TYPE")
                .bizId("BIZ-" + id)
                .status(TaskStatus.RUNNING)
                .workerId("worker-" + id)
                .lockedAt(LocalDateTime.now().minusMinutes(10))
                .lockExpireAt(LocalDateTime.now().minusMinutes(5))
                .version(id.intValue())
                .build();
    }
}
