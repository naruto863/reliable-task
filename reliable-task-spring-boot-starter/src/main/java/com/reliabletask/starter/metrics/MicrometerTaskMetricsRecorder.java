package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的任务指标记录器。
 */
public class MicrometerTaskMetricsRecorder implements TaskMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public MicrometerTaskMetricsRecorder(MeterRegistry meterRegistry,
                                         TaskStore taskStore,
                                         TaskExecutorFactory executorFactory) {
        this.meterRegistry = meterRegistry;
        registerGauges(meterRegistry, taskStore, executorFactory);
    }

    @Override
    public void record(TaskExecutionMetricsEvent event) {
        if (event == null) {
            return;
        }

        Tags tags = Tags.of(
                "task_type", valueOrUnknown(event.getTaskType()),
                "status", event.getStatus() == null ? "UNKNOWN" : event.getStatus().name(),
                "worker_id", valueOrUnknown(event.getWorkerId())
        );

        if (event.getStatus() == TaskStatus.PENDING) {
            increment("reliable_task_submitted_total", tags);
            return;
        }
        if (event.getStatus() == TaskStatus.SUCCESS || event.isSuccess()) {
            increment("reliable_task_success_total", tags);
            recordDuration(tags, event.getDurationMs());
            return;
        }
        if (event.getStatus() == TaskStatus.RETRYING) {
            increment("reliable_task_retry_total", tags);
            increment("reliable_task_failed_total", tags);
            recordDuration(tags, event.getDurationMs());
            return;
        }
        if (event.getStatus() == TaskStatus.DEAD || !event.isSuccess()) {
            increment("reliable_task_failed_total", tags);
            recordDuration(tags, event.getDurationMs());
        }
    }

    private void registerGauges(MeterRegistry registry,
                                TaskStore taskStore,
                                TaskExecutorFactory executorFactory) {
        Gauge.builder("reliable_task_pending_total", taskStore,
                        store -> store.getStats().getPendingTasks())
                .register(registry);
        Gauge.builder("reliable_task_running_total", taskStore,
                        store -> store.getStats().getStatusCount()
                                .getOrDefault(TaskStatus.RUNNING.getCode(), 0L))
                .register(registry);
        Gauge.builder("reliable_task_dead_total", taskStore,
                        store -> store.getStats().getDeadTasks())
                .register(registry);
        Gauge.builder("reliable_task_worker_available_capacity", executorFactory,
                        TaskExecutorFactory::getAvailableCapacity)
                .register(registry);
    }

    private void increment(String metricName, Tags tags) {
        Counter.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private void recordDuration(Tags tags, long durationMs) {
        Timer.builder("reliable_task_execution_duration")
                .tags(tags)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(durationMs, 0L)).toMillis(), TimeUnit.MILLISECONDS);
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
