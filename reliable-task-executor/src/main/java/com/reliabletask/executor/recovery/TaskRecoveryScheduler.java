package com.reliabletask.executor.recovery;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskCommandStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务恢复补偿扫描器
 *
 * <p>定时扫描 RUNNING 状态但锁租约已过期的任务，
 * 这些任务可能是以下原因遗留的孤儿任务:
 * <ul>
 *   <li>Worker 进程崩溃，任务未标记为失败</li>
 *   <li>TransactionSynchronization.afterCommit() 抛异常，任务未写入</li>
 *   <li>网络抖动导致 Worker 与数据库连接中断</li>
 * </ul>
 *
 * <p>状态流转: RUNNING (timeout) → PENDING
 *
 * <p>补偿扫描是防丢失的最后一道防线，确保任务最终能被重新消费。
 * 重置时携带 TaskExecutionLease，避免把已经被 Worker 续约或重新抢占的任务误恢复。
 */
@Slf4j
public class TaskRecoveryScheduler {

    private final TaskCommandStore taskStore;
    private final RecoveryProperties properties;
    private final TaskEventPublisher eventPublisher;

    public TaskRecoveryScheduler(TaskCommandStore taskStore, RecoveryProperties properties) {
        this(taskStore, properties, new TaskEventPublisher());
    }

    public TaskRecoveryScheduler(TaskCommandStore taskStore, RecoveryProperties properties,
                                 TaskEventPublisher eventPublisher) {
        this.taskStore = taskStore;
        this.properties = properties;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
    }

    /**
     * 定时补偿扫描
     *
     * <p>按配置的间隔执行，查找 RUNNING 超时任务并重置为 PENDING。
     * 如果开关关闭则跳过执行。
     */
    @Scheduled(fixedDelayString = "${reliable-task.recovery.interval-ms:30000}")
    public void scanAndRecover() {
        if (!properties.isEnabled()) {
            return;
        }

        int maxResetPerScan = Math.max(properties.getMaxResetPerScan(), 0);
        if (maxResetPerScan <= 0) {
            log.debug("Recovery skipped because maxResetPerScan is {}", maxResetPerScan);
            return;
        }

        // lockExpireAt 小于当前时间才算孤儿任务；租约仍有效的 RUNNING 任务必须留给当前 Worker 完成。
        LocalDateTime timeoutThreshold = LocalDateTime.now();
        List<TaskInstance> timeoutTasks = taskStore.findTimeoutTasks(timeoutThreshold, maxResetPerScan);

        if (timeoutTasks == null || timeoutTasks.isEmpty()) {
            return;
        }

        log.warn("Found {} timeout RUNNING tasks, starting recovery", timeoutTasks.size());

        int resetCount = 0;
        for (TaskInstance task : timeoutTasks) {
            if (resetCount >= maxResetPerScan) {
                log.warn("Reached max reset limit ({}), remaining tasks will be handled in next scan",
                        maxResetPerScan);
                break;
            }

            // 以扫描时看到的租约做 CAS，防止扫描与 Worker 续约、成功回写、其他恢复线程之间互相覆盖。
            boolean success = taskStore.resetTimeoutTask(TaskExecutionLease.from(task));
            if (success) {
                resetCount++;
                log.info("Recovered timeout task: id={}, type={}, bizId={}, lastUpdate={}",
                        task.getId(), task.getTaskType(), task.getBizId(), task.getUpdateTime());
                eventPublisher.publish(TaskEvent.of(TaskEventType.RECOVERED, task,
                        task.getStatus(), TaskStatus.PENDING, "timeout task recovered"));
            }
        }

        if (resetCount > 0) {
            log.info("Recovery scan completed, reset {} tasks to PENDING", resetCount);
        }
    }
}
