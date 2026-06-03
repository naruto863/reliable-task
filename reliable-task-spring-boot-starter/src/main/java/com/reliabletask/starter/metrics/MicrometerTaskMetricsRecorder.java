package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.starter.config.ReliableTaskProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * 基于 Micrometer 的任务指标记录器。
 */
public class MicrometerTaskMetricsRecorder implements TaskMetricsRecorder {

    private static final long DEFAULT_STATS_CACHE_TTL_MS = 5000L;

    private final MeterRegistry meterRegistry;
    private final boolean includeWorkerIdTag;
    private final StatsSnapshot statsSnapshot;

    public MicrometerTaskMetricsRecorder(MeterRegistry meterRegistry,
                                         TaskStore taskStore,
                                         TaskExecutorFactory executorFactory) {
        this(meterRegistry, taskStore, executorFactory, false, DEFAULT_STATS_CACHE_TTL_MS);
    }

    public MicrometerTaskMetricsRecorder(MeterRegistry meterRegistry,
                                         TaskStore taskStore,
                                         TaskExecutorFactory executorFactory,
                                         ReliableTaskProperties.Metrics metrics) {
        this(meterRegistry, taskStore, executorFactory,
                metrics != null && metrics.isIncludeWorkerIdTag(),
                metrics == null ? DEFAULT_STATS_CACHE_TTL_MS : metrics.getStatsCacheTtlMs());
    }

    public MicrometerTaskMetricsRecorder(MeterRegistry meterRegistry,
                                         TaskStore taskStore,
                                         TaskExecutorFactory executorFactory,
                                         boolean includeWorkerIdTag,
                                         long statsCacheTtlMs) {
        this.meterRegistry = meterRegistry;
        this.includeWorkerIdTag = includeWorkerIdTag;
        this.statsSnapshot = new StatsSnapshot(taskStore, statsCacheTtlMs);
        registerGauges(meterRegistry, statsSnapshot, executorFactory);
    }

    @Override
    public void record(TaskExecutionMetricsEvent event) {
        if (event == null) {
            return;
        }

        Tags tags = executionTags(event);

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

    private Tags executionTags(TaskExecutionMetricsEvent event) {
        Tags tags = Tags.of(
                "task_type", valueOrUnknown(event.getTaskType()),
                "status", event.getStatus() == null ? "UNKNOWN" : event.getStatus().name()
        );
        if (includeWorkerIdTag) {
            tags = tags.and("worker_id", valueOrUnknown(event.getWorkerId()));
        }
        return tags;
    }

    private void registerGauges(MeterRegistry registry,
                                StatsSnapshot snapshot,
                                TaskExecutorFactory executorFactory) {
        Gauge.builder("reliable_task_pending_total", snapshot,
                        stats -> stats.value(TaskStatsVO::getPendingTasks))
                .register(registry);
        Gauge.builder("reliable_task_backlog_total", snapshot,
                        stats -> stats.value(TaskStatsVO::getPendingTasks))
                .register(registry);
        Gauge.builder("reliable_task_running_total", snapshot,
                        stats -> stats.value(MicrometerTaskMetricsRecorder::runningTasks))
                .register(registry);
        Gauge.builder("reliable_task_dead_total", snapshot,
                        stats -> stats.value(TaskStatsVO::getDeadTasks))
                .register(registry);
        Gauge.builder("reliable_task_oldest_pending_age_seconds", snapshot,
                        stats -> stats.value(TaskStatsVO::getOldestPendingAgeSeconds))
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

    private static double runningTasks(TaskStatsVO stats) {
        Map<Integer, Long> statusCount = stats.getStatusCount();
        if (statusCount == null) {
            return 0D;
        }
        return statusCount.getOrDefault(TaskStatus.RUNNING.getCode(), 0L);
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static class StatsSnapshot {
        private final TaskStore taskStore;
        private final long ttlMs;
        private volatile TaskStatsVO cachedStats;
        private volatile long expiresAtMs;

        StatsSnapshot(TaskStore taskStore, long ttlMs) {
            this.taskStore = taskStore;
            this.ttlMs = Math.max(ttlMs, 1000L);
        }

        double value(ToDoubleFunction<TaskStatsVO> extractor) {
            return extractor.applyAsDouble(current());
        }

        private TaskStatsVO current() {
            long now = System.currentTimeMillis();
            TaskStatsVO current = cachedStats;
            if (current != null && now < expiresAtMs) {
                return current;
            }
            synchronized (this) {
                current = cachedStats;
                if (current != null && now < expiresAtMs) {
                    return current;
                }
                TaskStatsVO loaded = taskStore.getStats();
                cachedStats = loaded != null ? loaded : emptyStats();
                expiresAtMs = now + ttlMs;
                return cachedStats;
            }
        }

        private TaskStatsVO emptyStats() {
            TaskStatsVO stats = new TaskStatsVO();
            stats.setStatusCount(Map.of());
            stats.setTaskTypeStats(Map.of());
            return stats;
        }
    }
}
