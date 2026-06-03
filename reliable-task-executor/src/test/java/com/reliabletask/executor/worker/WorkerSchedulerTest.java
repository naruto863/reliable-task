package com.reliabletask.executor.worker;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.executor.handler.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WorkerScheduler 测试")
@ExtendWith(MockitoExtension.class)
class WorkerSchedulerTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private TaskExecutor taskExecutor;

    private WorkerProperties properties;
    private WorkerScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new WorkerProperties();
        properties.setEnabled(true);
        properties.setPollIntervalMs(5000L);
        properties.setBatchSize(10);

        scheduler = new WorkerScheduler(taskStore, properties, taskExecutor);
    }

    @Test
    @DisplayName("pollAndExecute - 开关关闭跳过执行")
    void pollAndExecute_disabled_skipsExecution() {
        properties.setEnabled(false);

        scheduler.pollAndExecute();

        verify(taskStore, never()).fetchPendingTasks(anyInt());
    }

    @Test
    @DisplayName("pollAndExecute - 无待执行任务不调用 claimTask")
    void pollAndExecute_noPendingTasks_noClaim() {
        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of());

        scheduler.pollAndExecute();

        verify(taskStore).fetchPendingTasks(10);
        verify(taskStore, never()).claimTask(anyLong(), anyString());
    }

    @Test
    @DisplayName("pollAndExecute - batchSize 超过上限时按 maxBatchSize 拉取")
    void pollAndExecute_batchSizeExceedsMax_fetchesMaxBatchSize() {
        properties.setBatchSize(5000);
        properties.setMaxBatchSize(100);
        when(taskStore.fetchPendingTasks(100)).thenReturn(List.of());

        scheduler.pollAndExecute();

        verify(taskStore).fetchPendingTasks(100);
    }

    @Test
    @DisplayName("pollAndExecute - store 返回 null 时按空列表处理")
    void pollAndExecute_nullPendingTasks_noClaim() {
        when(taskStore.fetchPendingTasks(10)).thenReturn(null);

        assertDoesNotThrow(() -> scheduler.pollAndExecute());

        verify(taskStore).fetchPendingTasks(10);
        verify(taskStore, never()).claimTask(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("pollAndExecute - 有任务时尝试抢占")
    void pollAndExecute_withTasks_attemptsClaim() {
        properties.setLockTtlSeconds(45L);
        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .status(TaskStatus.PENDING)
                .build();
        TaskInstance claimedTask = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .status(TaskStatus.RUNNING)
                .workerId("worker-claimed")
                .lockedAt(LocalDateTime.now())
                .lockExpireAt(LocalDateTime.now().plusSeconds(45))
                .version(2)
                .build();

        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of(task));
        when(taskStore.claimTask(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(taskStore.getById(1L)).thenReturn(claimedTask);

        scheduler.pollAndExecute();

        verify(taskStore).fetchPendingTasks(10);
        verify(taskStore).claimTask(eq(1L), anyString(), argThat(lockExpireAt -> {
            long seconds = java.time.Duration.between(LocalDateTime.now(), lockExpireAt).toSeconds();
            return seconds >= 43 && seconds <= 45;
        }));
        verify(taskExecutor).execute(claimedTask);
    }

    @Test
    @DisplayName("pollAndExecute - 抢占成功后发布 STARTED 事件")
    void pollAndExecute_claimSuccess_publishesStartedEvent() {
        List<TaskEvent> events = new ArrayList<>();
        scheduler = new WorkerScheduler(taskStore, properties, taskExecutor,
                new TaskEventPublisher(List.of(events::add)));
        TaskInstance task = TaskInstance.builder()
                .id(11L)
                .taskType("TYPE_A")
                .bizId("BIZ-11")
                .status(TaskStatus.RETRYING)
                .build();
        TaskInstance claimedTask = TaskInstance.builder()
                .id(11L)
                .taskType("TYPE_A")
                .bizId("BIZ-11")
                .status(TaskStatus.RUNNING)
                .workerId("worker-claimed")
                .traceId("trace-11")
                .build();
        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of(task));
        when(taskStore.claimTask(eq(11L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(taskStore.getById(11L)).thenReturn(claimedTask);

        scheduler.pollAndExecute();

        assertEquals(1, events.size());
        TaskEvent event = events.get(0);
        assertEquals(TaskEventType.STARTED, event.getEventType());
        assertEquals(TaskStatus.RETRYING, event.getStatusBefore());
        assertEquals(TaskStatus.RUNNING, event.getStatusAfter());
        assertEquals("worker-claimed", event.getWorkerId());
        assertEquals("trace-11", event.getTraceId());
    }

    @Test
    @DisplayName("pollAndExecute - 抢占后无法读取最新租约则不执行")
    void pollAndExecute_claimedTaskMissing_doesNotExecuteWithoutLease() {
        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .status(TaskStatus.PENDING)
                .build();

        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of(task));
        when(taskStore.claimTask(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(taskStore.getById(1L)).thenReturn(null);

        scheduler.pollAndExecute();

        verify(taskStore).getById(1L);
        verify(taskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("pollAndExecute - 抢占失败不执行")
    void pollAndExecute_claimFailure_doesNotExecute() {
        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .taskType("TYPE_A")
                .bizId("BIZ-1")
                .status(TaskStatus.PENDING)
                .build();

        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of(task));
        when(taskStore.claimTask(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(false);

        scheduler.pollAndExecute();

        verify(taskStore).claimTask(eq(1L), anyString(), any(LocalDateTime.class));
        verify(taskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("pollAndExecute - 单个任务异常不影响其他任务")
    void pollAndExecute_oneTaskException_continuesOthers() {
        TaskInstance t1 = TaskInstance.builder().id(1L).taskType("TYPE_A").bizId("BIZ-1").status(TaskStatus.PENDING).build();
        TaskInstance t2 = TaskInstance.builder().id(2L).taskType("TYPE_B").bizId("BIZ-2").status(TaskStatus.PENDING).build();

        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of(t1, t2));
        when(taskStore.claimTask(eq(1L), anyString(), any(LocalDateTime.class))).thenThrow(new RuntimeException("DB error"));
        when(taskStore.claimTask(eq(2L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(taskStore.getById(2L)).thenReturn(t2);

        scheduler.pollAndExecute();

        verify(taskStore, times(2)).claimTask(anyLong(), anyString(), any(LocalDateTime.class));
        verify(taskStore).claimTask(eq(2L), anyString(), any(LocalDateTime.class));
        verify(taskExecutor).execute(t2);
    }

    @Test
    @DisplayName("pollAndExecute - 背压容量为 0 时跳过拉取")
    void pollAndExecute_backpressureNoCapacity_skipsFetch() {
        properties.setBackpressureEnabled(true);
        when(taskExecutor.getAvailableCapacity()).thenReturn(0);

        scheduler.pollAndExecute();

        verify(taskStore, never()).fetchPendingTasks(anyInt());
    }

    @Test
    @DisplayName("pollAndExecute - 背压容量小于 batchSize 时按容量拉取")
    void pollAndExecute_backpressureCapacityLessThanBatch_fetchesAvailableCapacity() {
        properties.setBackpressureEnabled(true);
        properties.setBatchSize(10);
        properties.setBackpressureMinFetchSize(1);
        properties.setBackpressureMaxFetchSize(10);
        when(taskExecutor.getAvailableCapacity()).thenReturn(3);
        when(taskStore.fetchPendingTasks(3)).thenReturn(List.of());

        scheduler.pollAndExecute();

        verify(taskStore).fetchPendingTasks(3);
    }

    @Test
    @DisplayName("pollAndExecute - 背压容量大于 batchSize 时按 batchSize 拉取")
    void pollAndExecute_backpressureCapacityGreaterThanBatch_fetchesBatchSize() {
        properties.setBackpressureEnabled(true);
        properties.setBatchSize(10);
        properties.setBackpressureMaxFetchSize(20);
        when(taskExecutor.getAvailableCapacity()).thenReturn(30);
        when(taskStore.fetchPendingTasks(10)).thenReturn(List.of());

        scheduler.pollAndExecute();

        verify(taskStore).fetchPendingTasks(10);
    }

    @Test
    @DisplayName("pollAndExecute - 背压容量低于最小拉取量时跳过拉取")
    void pollAndExecute_backpressureCapacityLessThanMinimum_skipsFetch() {
        properties.setBackpressureEnabled(true);
        properties.setBackpressureMinFetchSize(5);
        when(taskExecutor.getAvailableCapacity()).thenReturn(3);

        scheduler.pollAndExecute();

        verify(taskStore, never()).fetchPendingTasks(anyInt());
    }

    @Test
    @DisplayName("reportHeartbeat - 心跳开启时上报 Worker 状态")
    void reportHeartbeat_enabled_reportsWorkerState() {
        properties.setHeartbeatEnabled(true);
        when(taskExecutor.getMaxCapacity()).thenReturn(10);
        when(taskExecutor.getAvailableCapacity()).thenReturn(7);

        scheduler.reportHeartbeat();

        ArgumentCaptor<WorkerHeartbeat> captor = ArgumentCaptor.forClass(WorkerHeartbeat.class);
        verify(taskStore).reportWorkerHeartbeat(captor.capture());
        WorkerHeartbeat heartbeat = captor.getValue();
        assertEquals("ONLINE", heartbeat.getStatus());
        assertEquals(3, heartbeat.getRunningTaskCount());
        assertEquals(10, heartbeat.getMaxConcurrency());
        assertEquals(7, heartbeat.getAvailableCapacity());
        assertTrue(heartbeat.getWorkerId().contains(":"));
    }

    @Test
    @DisplayName("reportHeartbeat - 心跳关闭时不写入")
    void reportHeartbeat_disabled_skipsReport() {
        scheduler.reportHeartbeat();

        verify(taskStore, never()).reportWorkerHeartbeat(any());
    }
}
