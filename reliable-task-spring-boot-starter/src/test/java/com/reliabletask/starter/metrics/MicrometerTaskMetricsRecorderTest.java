package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskQueryStore;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MicrometerTaskMetricsRecorder 测试")
class MicrometerTaskMetricsRecorderTest {

    @Test
    @DisplayName("record - 注册 Counter、Timer 和 Gauge")
    void record_registersCountersTimersAndGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskQueryStore taskStore = mock(TaskQueryStore.class);
        TaskExecutorFactory executorFactory = mock(TaskExecutorFactory.class);
        TaskStatsVO stats = new TaskStatsVO();
        stats.setPendingTasks(3L);
        stats.setDeadTasks(2L);
        stats.setOldestPendingAgeSeconds(120L);
        stats.setStatusCount(Map.of(TaskStatus.RUNNING.getCode(), 1L));
        when(taskStore.getStats()).thenReturn(stats);
        when(executorFactory.getAvailableCapacity()).thenReturn(7);

        MicrometerTaskMetricsRecorder recorder =
                new MicrometerTaskMetricsRecorder(registry, taskStore, executorFactory);

        recorder.record(TaskExecutionMetricsEvent.builder()
                .taskId(1L)
                .taskType("TYPE_A")
                .status(TaskStatus.PENDING)
                .success(true)
                .traceId("trace-high-cardinality")
                .build());
        recorder.record(TaskExecutionMetricsEvent.builder()
                .taskId(2L)
                .taskType("TYPE_A")
                .status(TaskStatus.SUCCESS)
                .workerId("worker-1")
                .durationMs(25)
                .success(true)
                .build());
        recorder.record(TaskExecutionMetricsEvent.builder()
                .taskId(3L)
                .taskType("TYPE_A")
                .status(TaskStatus.RETRYING)
                .workerId("worker-1")
                .durationMs(30)
                .success(false)
                .errorCode("RetryableException")
                .build());

        assertThat(registry.counter("reliable_task_submitted_total",
                "task_type", "TYPE_A", "status", "PENDING").count()).isEqualTo(1.0);
        assertThat(registry.counter("reliable_task_success_total",
                "task_type", "TYPE_A", "status", "SUCCESS").count()).isEqualTo(1.0);
        assertThat(registry.counter("reliable_task_retry_total",
                "task_type", "TYPE_A", "status", "RETRYING").count()).isEqualTo(1.0);
        assertThat(registry.timer("reliable_task_execution_duration",
                "task_type", "TYPE_A", "status", "SUCCESS").count()).isEqualTo(1L);
        assertThat(registry.find("reliable_task_success_total").tag("worker_id", "worker-1").counter())
                .isNull();
        assertThat(registry.find("reliable_task_submitted_total").meters()
                .iterator().next().getId().getTag("traceId"))
                .isNull();

        assertThat(registry.get("reliable_task_pending_total").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("reliable_task_backlog_total").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("reliable_task_running_total").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("reliable_task_dead_total").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("reliable_task_oldest_pending_age_seconds").gauge().value()).isEqualTo(120.0);
        assertThat(registry.get("reliable_task_worker_available_capacity").gauge().value()).isEqualTo(7.0);
        verify(taskStore, times(1)).getStats();
    }

    @Test
    @DisplayName("record - 显式开启时保留 worker_id tag")
    void record_includeWorkerIdTagEnabled_keepsWorkerTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskQueryStore taskStore = mock(TaskQueryStore.class);
        TaskExecutorFactory executorFactory = mock(TaskExecutorFactory.class);
        when(taskStore.getStats()).thenReturn(new TaskStatsVO());

        MicrometerTaskMetricsRecorder recorder =
                new MicrometerTaskMetricsRecorder(registry, taskStore, executorFactory, true, 5000L);

        recorder.record(TaskExecutionMetricsEvent.builder()
                .taskType("TYPE_A")
                .status(TaskStatus.SUCCESS)
                .workerId("worker-1")
                .durationMs(25)
                .success(true)
                .build());

        assertThat(registry.counter("reliable_task_success_total",
                "task_type", "TYPE_A", "status", "SUCCESS", "worker_id", "worker-1").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("TaskEventListener - RECOVERED 事件累加 recovery counter")
    void taskEventListener_recoveredEvent_recordsRecoveryCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTaskEventListener listener = new MicrometerTaskEventListener(registry);

        listener.onEvent(TaskEvent.builder()
                .eventType(TaskEventType.RECOVERED)
                .taskType("TYPE_A")
                .build());
        listener.onEvent(TaskEvent.builder()
                .eventType(TaskEventType.STARTED)
                .taskType("TYPE_A")
                .build());

        assertThat(registry.counter("reliable_task_recovered_total",
                "task_type", "TYPE_A").count()).isEqualTo(1.0);
    }
}
