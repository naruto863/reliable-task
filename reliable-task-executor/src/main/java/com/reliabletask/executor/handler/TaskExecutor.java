package com.reliabletask.executor.handler;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.executor.alert.NoopTaskAlertService;
import com.reliabletask.executor.alert.TaskAlertService;
import com.reliabletask.executor.interceptor.TaskExecutionInterceptor;
import com.reliabletask.executor.retry.RetryEngine;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.executor.worker.WorkerProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.*;

/**
 * 任务执行器
 *
 * <p>负责单个任务的完整执行生命周期:
 * <pre>
 *   1. 根据 taskType 查找 TaskHandler
 *   2. 通过线程池异步执行，支持超时控制
 *   3. 执行成功: RUNNING → SUCCESS
 *   4. 执行失败: 委托 RetryEngine 处理重试或标记 DEAD
 *   5. 记录执行日志
 * </pre>
 *
 * <p>状态流转说明:
 * <ul>
 *   <li>RUNNING → SUCCESS: Handler.execute() 正常返回</li>
 *   <li>RUNNING → RETRYING: 可重试异常且未超过最大重试次数（由 RetryEngine 处理）</li>
 *   <li>RUNNING → DEAD: 不可重试异常或超过最大重试次数（由 RetryEngine 处理）</li>
 * </ul>
 */
@Slf4j
public class TaskExecutor {

    private final TaskStore taskStore;
    private final TaskHandlerRegistry handlerRegistry;
    private final TaskExecutorFactory executorFactory;
    private final RetryEngine retryEngine;
    private final TaskExecutionInterceptor interceptor;
    private final TaskPayloadSerializer payloadSerializer;
    private final WorkerProperties workerProperties;
    private final TaskMetricsRecorder metricsRecorder;
    private final TaskAuditRecorder auditRecorder;
    private final TaskAlertService alertService;
    private final TaskEventPublisher eventPublisher;
    private final ScheduledExecutorService leaseRenewalExecutor;
    private final ConcurrentMap<String, Semaphore> concurrencyLimiters = new ConcurrentHashMap<>();

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor) {
        this(taskStore, handlerRegistry, executorFactory, retryEngine,
                interceptor, new JacksonTaskPayloadSerializer());
    }

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor,
                        TaskPayloadSerializer payloadSerializer) {
        this(taskStore, handlerRegistry, executorFactory, retryEngine,
                interceptor, payloadSerializer, new WorkerProperties());
    }

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor,
                        TaskPayloadSerializer payloadSerializer,
                        WorkerProperties workerProperties) {
        this(taskStore, handlerRegistry, executorFactory, retryEngine, interceptor,
                payloadSerializer, workerProperties, new NoopTaskMetricsRecorder(), new NoopTaskAuditRecorder());
    }

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor,
                        TaskPayloadSerializer payloadSerializer,
                        WorkerProperties workerProperties,
                        TaskMetricsRecorder metricsRecorder,
                        TaskAuditRecorder auditRecorder) {
        this(taskStore, handlerRegistry, executorFactory, retryEngine, interceptor,
                payloadSerializer, workerProperties, metricsRecorder, auditRecorder,
                new NoopTaskAlertService());
    }

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor,
                        TaskPayloadSerializer payloadSerializer,
                        WorkerProperties workerProperties,
                        TaskMetricsRecorder metricsRecorder,
                        TaskAuditRecorder auditRecorder,
                        TaskAlertService alertService) {
        this(taskStore, handlerRegistry, executorFactory, retryEngine, interceptor,
                payloadSerializer, workerProperties, metricsRecorder, auditRecorder,
                alertService, new TaskEventPublisher());
    }

    public TaskExecutor(TaskStore taskStore, TaskHandlerRegistry handlerRegistry,
                        TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                        TaskExecutionInterceptor interceptor,
                        TaskPayloadSerializer payloadSerializer,
                        WorkerProperties workerProperties,
                        TaskMetricsRecorder metricsRecorder,
                        TaskAuditRecorder auditRecorder,
                        TaskAlertService alertService,
                        TaskEventPublisher eventPublisher) {
        this.taskStore = taskStore;
        this.handlerRegistry = handlerRegistry;
        this.executorFactory = executorFactory;
        this.retryEngine = retryEngine;
        this.interceptor = interceptor;
        this.payloadSerializer = payloadSerializer;
        this.workerProperties = workerProperties != null ? workerProperties : new WorkerProperties();
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new NoopTaskMetricsRecorder();
        this.auditRecorder = auditRecorder != null ? auditRecorder : new NoopTaskAuditRecorder();
        this.alertService = alertService != null ? alertService : new NoopTaskAlertService();
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
        this.leaseRenewalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "reliable-task-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 执行单个任务
     *
     * <p>完整流程: 查找 Handler → 异步执行 → 超时控制 → 状态更新 → 日志记录
     *
     * @param task 待执行的任务实例
     */
    public void execute(TaskInstance task) {
        // 执行调度线程前: 设置 traceId 到 MDC，保证调度日志可追踪。
        interceptor.beforeExecute(task);

        try {
            TaskHandler handler;
            try {
                handler = handlerRegistry.getHandler(task.getTaskType());
            } catch (IllegalArgumentException e) {
                log.error("No handler found for task: id={}, type={}", task.getId(), task.getTaskType());
                if (!markDead(task, "NO_HANDLER", e.getMessage())) {
                    log.warn("Skip no-handler DEAD log because lease CAS failed: id={}, workerId={}, version={}",
                            task.getId(), task.getWorkerId(), task.getVersion());
                    return;
                }
                taskStore.saveLog(task.getId(), task.getExecuteCount(),
                        "RUNNING", "DEAD", false, 0, "NO_HANDLER", e.getMessage(),
                        task.getWorkerId(), task.getTraceId());
                recordMetrics(task, TaskStatus.DEAD, 0, false, "NO_HANDLER");
                recordAudit(task, "SYSTEM_NO_HANDLER_DEAD", "SUCCESS", e.getMessage());
                notifyDead(task, "no handler found: " + e.getMessage());
                publishEvent(TaskEventType.DEAD, task, TaskStatus.RUNNING, TaskStatus.DEAD, "NO_HANDLER");
                return;
            }

            long timeoutMs = handler.timeoutMs();
            long startTime = System.currentTimeMillis();
            TaskStatus statusBefore = TaskStatus.RUNNING;

            try {
                TaskHandler taskHandler = handler;
                Future<?> future;
                try {
                    ExecutorService executor = executorFactory.getExecutor(task.getTaskType());
                    future = executor.submit(() -> {
                        interceptor.beforeExecute(task);
                        Semaphore limiter = resolveConcurrencyLimiter(taskHandler);
                        boolean acquired = false;
                        try {
                            if (limiter != null) {
                                limiter.acquire();
                                acquired = true;
                            }
                            Object typedPayload = deserializePayload(taskHandler, task);
                            taskHandler.execute(task, typedPayload);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException(e);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        } finally {
                            if (acquired) {
                                limiter.release();
                            }
                            interceptor.afterExecute();
                        }
                    });
                } catch (RuntimeException e) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    retryEngine.handleFailure(handler, task, e, durationMs, statusBefore.name());
                    return;
                }

                ScheduledFuture<?> leaseRenewal = startLeaseRenewal(task);
                try {
                    future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw e;
                } finally {
                    if (leaseRenewal != null) {
                        leaseRenewal.cancel(false);
                    }
                }

                long durationMs = System.currentTimeMillis() - startTime;
                if (!markSuccess(task)) {
                    log.warn("Skip success log because lease CAS failed: id={}, workerId={}, version={}",
                            task.getId(), task.getWorkerId(), task.getVersion());
                    return;
                }
                taskStore.saveLog(task.getId(), task.getExecuteCount(),
                        statusBefore.name(), "SUCCESS", true, durationMs, null, null,
                        task.getWorkerId(), task.getTraceId());
                recordMetrics(task, TaskStatus.SUCCESS, durationMs, true, null);
                log.info("Task executed successfully: id={}, type={}, bizId={}, duration={}ms",
                        task.getId(), task.getTaskType(), task.getBizId(), durationMs);
                publishEvent(TaskEventType.SUCCEEDED, task, statusBefore, TaskStatus.SUCCESS, "task succeeded");

            } catch (TimeoutException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                retryEngine.handleFailure(handler, task, new RuntimeException(
                        "Execution timeout after " + timeoutMs + "ms"), durationMs, statusBefore.name());
            } catch (ExecutionException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                retryEngine.handleFailure(handler, task, e, durationMs, statusBefore.name());
            } catch (InterruptedException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                Thread.currentThread().interrupt();
                retryEngine.handleFailure(handler, task, e, durationMs, statusBefore.name());
            }
        } finally {
            // 执行调度线程后: 清理 MDC，防止线程复用导致 traceId 泄漏。
            interceptor.afterExecute();
        }
    }

    public int getAvailableCapacity() {
        return executorFactory.getAvailableCapacity();
    }

    public int getMaxCapacity() {
        return executorFactory.getMaxCapacity();
    }

    @PreDestroy
    public void shutdown() {
        leaseRenewalExecutor.shutdownNow();
    }

    private Object deserializePayload(TaskHandler taskHandler, TaskInstance task) {
        Class<?> payloadType = taskHandler.payloadType();
        if (payloadType == null || payloadType == String.class) {
            return task.getPayload();
        }
        return payloadSerializer.deserialize(task.getPayload(), payloadType);
    }

    private Semaphore resolveConcurrencyLimiter(TaskHandler taskHandler) {
        int maxConcurrency = taskHandler.maxConcurrency();
        if (maxConcurrency <= 0) {
            return null;
        }
        return concurrencyLimiters.computeIfAbsent(taskHandler.getTaskType(),
                ignored -> new Semaphore(maxConcurrency));
    }

    private ScheduledFuture<?> startLeaseRenewal(TaskInstance task) {
        if (!workerProperties.isHeartbeatEnabled()
                || task.getId() == null
                || task.getWorkerId() == null
                || task.getWorkerId().isBlank()) {
            return null;
        }

        long intervalMs = Math.max(workerProperties.getHeartbeatIntervalMs(), 1000L);
        renewLease(task);
        return leaseRenewalExecutor.scheduleAtFixedRate(
                () -> renewLease(task), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private boolean markSuccess(TaskInstance task) {
        TaskExecutionLease lease = TaskExecutionLease.from(task);
        boolean updated = taskStore.markSuccess(lease);
        if (!updated && !hasExecutionLeaseIdentity(lease)) {
            return taskStore.markSuccess(task.getId());
        }
        return updated;
    }

    private boolean markDead(TaskInstance task, String errorCode, String errorMessage) {
        TaskExecutionLease lease = TaskExecutionLease.from(task);
        boolean updated = taskStore.markDead(lease, errorCode, errorMessage);
        if (!updated && !hasExecutionLeaseIdentity(lease)) {
            return taskStore.markDead(task.getId(), errorCode, errorMessage);
        }
        return updated;
    }

    private boolean hasExecutionLeaseIdentity(TaskExecutionLease lease) {
        return lease != null
                && lease.getTaskId() != null
                && lease.getWorkerId() != null
                && !lease.getWorkerId().isBlank()
                && lease.getVersion() != null;
    }

    private void renewLease(TaskInstance task) {
        try {
            LocalDateTime now = LocalDateTime.now();
            taskStore.renewTaskLease(task.getId(), task.getWorkerId(), now,
                    now.plusSeconds(workerProperties.getLockRenewalTtlSeconds()));
        } catch (RuntimeException e) {
            log.warn("Failed to renew task lease: id={}, workerId={}, reason={}",
                    task.getId(), task.getWorkerId(), e.getMessage());
        }
    }

    private void recordMetrics(TaskInstance task, TaskStatus status, long durationMs,
                               boolean success, String errorCode) {
        try {
            TaskExecutionMetricsEvent event = TaskExecutionMetricsEvent.builder()
                    .taskId(task.getId())
                    .taskType(task.getTaskType())
                    .status(status)
                    .workerId(task.getWorkerId())
                    .durationMs(durationMs)
                    .success(success)
                    .retryCount(task.getActualRetryCount())
                    .errorCode(errorCode)
                    .traceId(task.getTraceId())
                    .eventTime(LocalDateTime.now())
                    .build();
            metricsRecorder.record(event);
            alertService.recordMetricsEvent(event);
        } catch (RuntimeException e) {
            log.warn("Failed to record task metrics event: taskId={}, status={}, reason={}",
                    task.getId(), status, e.getMessage());
        }
    }

    private void notifyDead(TaskInstance task, String reason) {
        try {
            alertService.notifyDead(task, reason);
        } catch (RuntimeException e) {
            log.warn("Failed to dispatch dead task alert: taskId={}, reason={}",
                    task.getId(), e.getMessage());
        }
    }

    private void recordAudit(TaskInstance task, String operationType, String result, String errorMsg) {
        try {
            auditRecorder.record(AuditLog.builder()
                    .operationType(operationType)
                    .operator("SYSTEM")
                    .targetType("TASK")
                    .targetId(task.getId() == null ? null : String.valueOf(task.getId()))
                    .taskId(task.getId())
                    .requestSummary("taskType=" + task.getTaskType() + ", bizId=" + task.getBizId())
                    .result(result)
                    .errorMsg(errorMsg)
                    .traceId(task.getTraceId())
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to record task audit event: taskId={}, operationType={}, reason={}",
                    task.getId(), operationType, e.getMessage());
        }
    }

    private void publishEvent(TaskEventType eventType, TaskInstance task,
                              TaskStatus statusBefore, TaskStatus statusAfter, String reason) {
        eventPublisher.publish(TaskEvent.of(eventType, task, statusBefore, statusAfter, reason));
    }
}
