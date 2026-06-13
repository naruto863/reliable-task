package com.reliabletask.executor.worker;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.TaskCommandStore;
import com.reliabletask.core.spi.TaskOperationsStore;
import com.reliabletask.executor.handler.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker 定时调度器
 *
 * <p>按配置间隔定时从数据库拉取待执行任务，尝试抢占并执行。
 *
 * <p>工作流程:
 * <pre>
 *   定时触发 → fetchPendingTasks(batchSize) → 获取 PENDING/RETRYING 任务
 *   → 遍历每个任务 → claimTask(workerId) 抢占
 *   → 抢占成功: 执行任务（委托给执行引擎）
 *   → 抢占失败: 跳过（被其他 Worker 抢占）
 * </pre>
 *
 * <p>并发安全:
 * <ul>
 *   <li>fetchPendingTasks 返回的任务可能被多个 Worker 同时获取</li>
 *   <li>claimTask 通过 WHERE status = PENDING 状态锁保证只有一个 Worker 抢占成功</li>
 * </ul>
 */
@Slf4j
public class WorkerScheduler {

    private final TaskCommandStore taskCommandStore;
    private final TaskOperationsStore taskOperationsStore;
    private final WorkerProperties properties;
    private final TaskExecutor taskExecutor;
    private final TaskEventPublisher eventPublisher;

    public WorkerScheduler(TaskCommandStore taskStore, WorkerProperties properties, TaskExecutor taskExecutor) {
        this(taskStore, properties, taskExecutor, new TaskEventPublisher());
    }

    public WorkerScheduler(TaskCommandStore taskStore, WorkerProperties properties,
                           TaskExecutor taskExecutor, TaskEventPublisher eventPublisher) {
        this(taskStore, asOperationsStore(taskStore), properties, taskExecutor, eventPublisher);
    }

    public WorkerScheduler(TaskCommandStore taskCommandStore,
                           TaskOperationsStore taskOperationsStore,
                           WorkerProperties properties,
                           TaskExecutor taskExecutor,
                           TaskEventPublisher eventPublisher) {
        this.taskCommandStore = taskCommandStore;
        this.taskOperationsStore = taskOperationsStore;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
    }

    /**
     * 定时拉取并执行任务
     *
     * <p>如果开关关闭则跳过执行。
     * 拉取任务后逐个尝试抢占，抢占成功则执行。
     * 单个任务执行异常不影响其他任务。
     */
    @Scheduled(fixedDelayString = "${reliable-task.worker.poll-interval-ms:5000}")
    public void pollAndExecute() {
        if (!properties.isEnabled()) {
            return;
        }

        int fetchSize = resolveFetchSize();
        if (fetchSize <= 0) {
            return;
        }

        List<TaskInstance> tasks = taskCommandStore.fetchPendingTasks(fetchSize);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        String workerId = WorkerIdGenerator.getWorkerId();
        log.debug("Worker {} fetched {} tasks, attempting to claim", workerId, tasks.size());

        int claimedCount = 0;
        for (TaskInstance task : tasks) {
            try {
                LocalDateTime lockExpireAt = LocalDateTime.now()
                        .plusSeconds(Math.max(properties.getLockTtlSeconds(), 1L));
                boolean claimed = taskCommandStore.claimTask(task.getId(), workerId, lockExpireAt);
                if (claimed) {
                    claimedCount++;
                    log.info("Worker {} claimed task: id={}, type={}, bizId={}",
                            workerId, task.getId(), task.getTaskType(), task.getBizId());

                    TaskInstance claimedTask = taskCommandStore.getById(task.getId());
                    if (claimedTask == null) {
                        log.warn("Worker {} claimed task but failed to load latest lease: id={}",
                                workerId, task.getId());
                        continue;
                    }
                    publishStarted(task, claimedTask);
                    taskExecutor.execute(claimedTask);
                }
            } catch (Exception e) {
                log.error("Failed to claim task: id={}, type={}, bizId={}",
                        task.getId(), task.getTaskType(), task.getBizId(), e);
            }
        }

        if (claimedCount > 0) {
            log.info("Worker {} claimed {} tasks in this poll", workerId, claimedCount);
        }
    }

    @Scheduled(fixedDelayString = "${reliable-task.worker.heartbeat.interval-ms:10000}")
    public void reportHeartbeat() {
        if (!properties.isEnabled() || !properties.isHeartbeatEnabled()) {
            return;
        }

        String workerId = WorkerIdGenerator.getWorkerId();
        int maxCapacity = taskExecutor.getMaxCapacity();
        int availableCapacity = taskExecutor.getAvailableCapacity();
        int runningTaskCount = Math.max(maxCapacity - availableCapacity, 0);

        if (taskOperationsStore == null) {
            log.debug("Worker heartbeat skipped because TaskOperationsStore is not configured");
            return;
        }

        taskOperationsStore.reportWorkerHeartbeat(WorkerHeartbeat.builder()
                .workerId(workerId)
                .status("ONLINE")
                .runningTaskCount(runningTaskCount)
                .maxConcurrency(maxCapacity)
                .availableCapacity(availableCapacity)
                .lastHeartbeatTime(LocalDateTime.now())
                .build());
    }

    private static TaskOperationsStore asOperationsStore(TaskCommandStore taskStore) {
        return taskStore instanceof TaskOperationsStore operationsStore ? operationsStore : null;
    }

    private int resolveFetchSize() {
        int maxBatchSize = properties.getMaxBatchSize() > 0 ? properties.getMaxBatchSize() : 1000;
        int batchSize = Math.min(Math.max(properties.getBatchSize(), 0), maxBatchSize);
        if (!properties.isBackpressureEnabled()) {
            return batchSize;
        }

        int maxFetchSize = properties.getBackpressureMaxFetchSize() > 0
                ? properties.getBackpressureMaxFetchSize()
                : batchSize;
        int upperBound = Math.min(batchSize, maxFetchSize);
        if (upperBound <= 0) {
            log.debug("Worker backpressure skipped polling because upper fetch bound is {}", upperBound);
            return 0;
        }

        int minFetchSize = Math.max(properties.getBackpressureMinFetchSize(), 1);
        int effectiveMinFetchSize = Math.min(minFetchSize, upperBound);
        int availableCapacity = taskExecutor.getAvailableCapacity();
        if (availableCapacity < effectiveMinFetchSize) {
            log.info("Worker backpressure skipped polling: availableCapacity={}, minFetchSize={}, batchSize={}",
                    availableCapacity, effectiveMinFetchSize, batchSize);
            return 0;
        }

        int fetchSize = Math.min(availableCapacity, upperBound);
        if (fetchSize < batchSize) {
            log.debug("Worker backpressure adjusted fetch size: batchSize={}, fetchSize={}, availableCapacity={}",
                    batchSize, fetchSize, availableCapacity);
        }
        return fetchSize;
    }

    private void publishStarted(TaskInstance fetchedTask, TaskInstance claimedTask) {
        TaskStatus statusBefore = fetchedTask == null ? null : fetchedTask.getStatus();
        eventPublisher.publish(TaskEvent.of(TaskEventType.STARTED, claimedTask,
                statusBefore, TaskStatus.RUNNING, "task claimed"));
    }
}
