package com.reliabletask.executor.alert;

import com.reliabletask.core.spi.TaskQueryStore;
import com.reliabletask.core.vo.TaskStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 周期性告警扫描器。
 *
 * <p>该扫描器只负责 pending/backlog 这类需要全局统计的告警；
 * 单任务死信和执行失败率由执行链路即时触发，避免把所有告警都堆到同一个定时任务里。
 */
@Slf4j
public class TaskAlertScheduler {

    private final TaskQueryStore taskStore;
    private final AlertProperties properties;
    private final TaskAlertService alertService;

    public TaskAlertScheduler(TaskQueryStore taskStore, AlertProperties properties, TaskAlertService alertService) {
        this.taskStore = taskStore;
        this.properties = properties;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${reliable-task.alert.scan-interval-ms:30000}")
    public void scanPendingBacklog() {
        if (!properties.isEnabled() || properties.getPendingThreshold() <= 0) {
            return;
        }
        try {
            // 使用 TaskQueryStore 的读模型统计，避免扫描器感知具体存储实现或 SQL 细节。
            TaskStatsVO stats = taskStore.getStats();
            long pendingTasks = stats == null ? 0L : stats.getPendingTasks();
            if (pendingTasks > properties.getPendingThreshold()) {
                alertService.notifyPendingBacklog(pendingTasks, properties.getPendingThreshold());
            }
        } catch (RuntimeException e) {
            log.warn("Failed to scan pending backlog alarm: reason={}", e.getMessage());
        }
    }
}
