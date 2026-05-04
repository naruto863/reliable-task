package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MicrometerTaskMetricsRecorder 测试")
class MicrometerTaskMetricsRecorderTest {

    @Test
    @DisplayName("record - 注册 Counter、Timer 和 Gauge")
    void record_registersCountersTimersAndGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskStore taskStore = mock(TaskStore.class);
        TaskExecutorFactory executorFactory = mock(TaskExecutorFactory.class);
        TaskStatsVO stats = new TaskStatsVO();
        stats.setPendingTasks(3L);
        stats.setDeadTasks(2L);
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
                "task_type", "TYPE_A", "status", "PENDING", "worker_id", "UNKNOWN").count()).isEqualTo(1.0);
        assertThat(registry.counter("reliable_task_success_total",
                "task_type", "TYPE_A", "status", "SUCCESS", "worker_id", "worker-1").count()).isEqualTo(1.0);
        assertThat(registry.counter("reliable_task_retry_total",
                "task_type", "TYPE_A", "status", "RETRYING", "worker_id", "worker-1").count()).isEqualTo(1.0);
        assertThat(registry.timer("reliable_task_execution_duration",
                "task_type", "TYPE_A", "status", "SUCCESS", "worker_id", "worker-1").count()).isEqualTo(1L);
        assertThat(registry.find("reliable_task_submitted_total").meters()
                .iterator().next().getId().getTag("traceId"))
                .isNull();

        assertThat(registry.get("reliable_task_pending_total").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("reliable_task_running_total").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("reliable_task_dead_total").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("reliable_task_worker_available_capacity").gauge().value()).isEqualTo(7.0);
    }
}
