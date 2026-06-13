package com.reliabletask.executor.retry;

import com.reliabletask.core.classifier.DefaultFailureClassifier;
import com.reliabletask.core.deadletter.TaskDeadLetterDispatcher;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.diagnostics.DefaultTaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskFailureDiagnostic;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.DeadLetterContext;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.FailureClassifier;
import com.reliabletask.core.spi.RetryStrategy;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskCommandStore;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.strategy.RetryStrategyRegistry;
import com.reliabletask.executor.alert.NoopTaskAlertService;
import com.reliabletask.executor.alert.TaskAlertService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 重试引擎
 *
 * <p>负责任务失败后的重试决策和状态流转:
 * <pre>
 *   1. 解包异常，找到最内层的业务异常
 *   2. 通过 RetryStrategyResolver 解析注解和配置，获取最终重试参数
 *   3. 判断是否可重试 (NonRetryableException → 不可重试)
 *   4. 判断是否耗尽最大重试次数（maxRetryCount 不含首次执行）
 *   5. 不可重试/耗尽: RUNNING → DEAD
 *   6. 可重试: 计算延迟 → RUNNING → RETRYING
 * </pre>
 *
 * <p>状态流转说明:
 * <ul>
 *   <li>RUNNING → DEAD: 抛出 NonRetryableException 或实际已重试次数达到 maxRetryCount</li>
 *   <li>RUNNING → RETRYING: 可重试且未超过最大重试次数，通过重试策略计算下次执行时间</li>
 * </ul>
 */
@Slf4j
public class RetryEngine {

    /**
     * 任务存储，用于更新状态和记录日志
     */
    private final TaskCommandStore taskStore;
    private final TaskMetricsRecorder metricsRecorder;
    private final TaskAuditRecorder auditRecorder;
    private final TaskAlertService alertService;
    private final TaskExceptionFormatter exceptionFormatter;
    private final RetryStrategyRegistry retryStrategyRegistry;
    private final RetryProperties retryProperties;
    private final FailureClassifier failureClassifier;
    private final TaskEventPublisher eventPublisher;
    private final TaskDeadLetterDispatcher deadLetterDispatcher;
    private final FailureClassifier defaultFailureClassifier = new DefaultFailureClassifier();

    public RetryEngine(TaskCommandStore taskStore) {
        this(taskStore, new NoopTaskMetricsRecorder(), new NoopTaskAuditRecorder());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder) {
        this(taskStore, metricsRecorder, auditRecorder, new NoopTaskAlertService());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, new DefaultTaskExceptionFormatter());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, exceptionFormatter,
                new RetryStrategyRegistry(), new RetryProperties());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter,
                       RetryStrategyRegistry retryStrategyRegistry,
                       RetryProperties retryProperties) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, exceptionFormatter,
                retryStrategyRegistry, retryProperties, new DefaultFailureClassifier());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter,
                       RetryStrategyRegistry retryStrategyRegistry,
                       RetryProperties retryProperties,
                       FailureClassifier failureClassifier) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, exceptionFormatter,
                retryStrategyRegistry, retryProperties, failureClassifier, new TaskEventPublisher());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter,
                       RetryStrategyRegistry retryStrategyRegistry,
                       RetryProperties retryProperties,
                       FailureClassifier failureClassifier,
                       TaskEventPublisher eventPublisher) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, exceptionFormatter,
                retryStrategyRegistry, retryProperties, failureClassifier, eventPublisher,
                new TaskDeadLetterDispatcher());
    }

    public RetryEngine(TaskCommandStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter,
                       RetryStrategyRegistry retryStrategyRegistry,
                       RetryProperties retryProperties,
                       FailureClassifier failureClassifier,
                       TaskEventPublisher eventPublisher,
                       TaskDeadLetterDispatcher deadLetterDispatcher) {
        this.taskStore = taskStore;
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new NoopTaskMetricsRecorder();
        this.auditRecorder = auditRecorder != null ? auditRecorder : new NoopTaskAuditRecorder();
        this.alertService = alertService != null ? alertService : new NoopTaskAlertService();
        this.exceptionFormatter = exceptionFormatter != null
                ? exceptionFormatter
                : new DefaultTaskExceptionFormatter();
        this.retryStrategyRegistry = retryStrategyRegistry != null
                ? retryStrategyRegistry
                : new RetryStrategyRegistry();
        this.retryProperties = retryProperties != null ? retryProperties : new RetryProperties();
        this.failureClassifier = failureClassifier != null
                ? failureClassifier
                : new DefaultFailureClassifier();
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
        this.deadLetterDispatcher = deadLetterDispatcher != null
                ? deadLetterDispatcher
                : new TaskDeadLetterDispatcher();
    }

    /**
     * 处理任务执行失败
     *
     * <p>根据异常类型和重试次数决定下一步:
     * <ul>
     *   <li>NonRetryableException: 直接标记 DEAD</li>
     *   <li>超过最大重试次数: 标记 DEAD</li>
     *   <li>其他: 计算下次重试时间，标记 RETRYING</li>
     * </ul>
     *
     * @param handler      执行该任务的 Handler（用于解析 @TaskRetryable 注解）
     * @param task         失败的任务实例
     * @param error        原始异常（可能被包装）
     * @param durationMs   执行耗时（毫秒）
     * @param statusBefore 执行前的状态名称
     */
    public void handleFailure(TaskHandler handler, TaskInstance task,
                              Throwable error, long durationMs, String statusBefore) {
        Throwable rootCause = unwrap(error);

        // 通过 Resolver 解析注解和配置，获取最终生效的重试参数
        RetryStrategyResolver.ResolvedRetryConfig config =
                RetryStrategyResolver.resolve(handler, task, retryProperties);

        FailureDecision failureDecision = classifyFailure(task, rootCause);
        boolean retriesExhausted = task.getActualRetryCount() >= config.getMaxRetryCount();

        if (failureDecision.getAction() == FailureDecision.Action.DEAD || retriesExhausted) {
            markDead(task, rootCause, durationMs, statusBefore, retriesExhausted, failureDecision);
        } else {
            markWaitRetry(task, rootCause, durationMs, statusBefore, config);
        }
    }

    /**
     * 标记任务为 DEAD 状态
     *
     * <p>状态流转: RUNNING → DEAD
     * 同时记录执行日志。
     */
    private void markDead(TaskInstance task, Throwable error, long durationMs,
                          String statusBefore, boolean retriesExhausted,
                          FailureDecision failureDecision) {
        TaskFailureDiagnostic diagnostic = exceptionFormatter.format(error);
        String errorCode = diagnostic.getErrorCode();
        String errorMessage = diagnostic.getErrorMessage();
        String logMessage = diagnostic.getStackTrace() != null ? diagnostic.getStackTrace() : errorMessage;
        if (!markDead(task, errorCode, truncate(errorMessage, 2000))) {
            log.warn("Skip DEAD log because lease CAS failed: id={}, workerId={}, version={}, errorCode={}",
                    task.getId(), task.getWorkerId(), task.getVersion(), errorCode);
            return;
        }
        taskStore.saveLog(task.getId(), task.getExecuteCount(),
                statusBefore, "DEAD", false, durationMs,
                errorCode, truncate(logMessage, 4000),
                task.getWorkerId(), task.getTraceId());
        log.warn("Task marked DEAD: id={}, type={}, bizId={}, reason={}, exhausted={}, decision={}",
                task.getId(), task.getTaskType(), task.getBizId(),
                errorMessage, retriesExhausted, failureDecision.getReason());
        recordMetrics(task, TaskStatus.DEAD, durationMs, false, errorCode);
        recordAudit(task, deadOperationType(error, retriesExhausted),
                "SUCCESS",
                appendDecisionReason(errorMessage, failureDecision));
        notifyDead(task, retriesExhausted ? "max retry exceeded: " + errorMessage
                : deadNotifyReason(error, errorMessage, failureDecision));
        publishEvent(TaskEventType.DEAD, task, parseStatus(statusBefore), TaskStatus.DEAD, errorCode);
        dispatchDeadLetter(task, errorCode, errorMessage,
                retriesExhausted ? "max retry exceeded" : failureDecision.getReason(),
                retriesExhausted);
    }

    /**
     * 标记任务为 RETRYING 状态
     *
     * <p>状态流转: RUNNING → RETRYING
     * 通过重试策略计算下次执行时间，同时记录执行日志。
     */
    private void markWaitRetry(TaskInstance task, Throwable error, long durationMs,
                               String statusBefore, RetryStrategyResolver.ResolvedRetryConfig config) {
        RetryStrategy strategy = retryStrategyRegistry.getStrategy(config.getStrategy());
        long rawDelayMs = strategy.nextDelayMs(
                task.getActualRetryCount(),
                config.getRetryIntervalMs(),
                config.getMaxDelayMs()
        );
        long delayMs = clampDelay(rawDelayMs, config);
        LocalDateTime nextExecuteTime = LocalDateTime.now().plus(Duration.ofMillis(delayMs));

        TaskFailureDiagnostic diagnostic = exceptionFormatter.format(error);
        String errorCode = diagnostic.getErrorCode();
        String errorMessage = diagnostic.getErrorMessage();
        String logMessage = diagnostic.getStackTrace() != null ? diagnostic.getStackTrace() : errorMessage;
        if (!markWaitRetry(task, errorCode, truncate(errorMessage, 2000), nextExecuteTime)) {
            log.warn("Skip RETRYING log because lease CAS failed: id={}, workerId={}, version={}, errorCode={}",
                    task.getId(), task.getWorkerId(), task.getVersion(), errorCode);
            return;
        }
        taskStore.saveLog(task.getId(), task.getExecuteCount(),
                statusBefore, "RETRYING", false, durationMs,
                errorCode, truncate(logMessage, 4000),
                task.getWorkerId(), task.getTraceId());
        log.info("Task marked RETRYING: id={}, type={}, bizId={}, nextExecuteTime={}, delay={}ms",
                task.getId(), task.getTaskType(), task.getBizId(), nextExecuteTime, delayMs);
        recordMetrics(task, TaskStatus.RETRYING, durationMs, false, errorCode);
        recordAudit(task, "SYSTEM_RETRY_SCHEDULED", "SUCCESS", errorMessage);
        publishEvent(TaskEventType.RETRY_SCHEDULED, task, parseStatus(statusBefore),
                TaskStatus.RETRYING, errorCode);
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

    private boolean markDead(TaskInstance task, String errorCode, String errorMessage) {
        TaskExecutionLease lease = TaskExecutionLease.from(task);
        boolean updated = taskStore.markDead(lease, errorCode, errorMessage);
        if (!updated && !hasExecutionLeaseIdentity(lease)) {
            return taskStore.markDead(task.getId(), errorCode, errorMessage);
        }
        return updated;
    }

    private boolean markWaitRetry(TaskInstance task, String errorCode, String errorMessage,
                                  LocalDateTime nextExecuteTime) {
        TaskExecutionLease lease = TaskExecutionLease.from(task);
        boolean updated = taskStore.markWaitRetry(lease, errorCode, errorMessage, nextExecuteTime);
        if (!updated && !hasExecutionLeaseIdentity(lease)) {
            return taskStore.markWaitRetry(task.getId(), errorCode, errorMessage, nextExecuteTime);
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

    private long clampDelay(long delayMs, RetryStrategyResolver.ResolvedRetryConfig config) {
        long normalizedDelay = Math.max(0L, delayMs);
        normalizedDelay = Math.max(normalizedDelay, config.getMinDelayMs());
        return Math.min(normalizedDelay, config.getMaxDelayMs());
    }

    private FailureDecision classifyFailure(TaskInstance task, Throwable rootCause) {
        try {
            FailureDecision decision = failureClassifier.classify(task, rootCause);
            if (decision != null && decision.getAction() != null) {
                return decision;
            }
            log.warn("FailureClassifier returned empty decision, fallback to default: taskId={}", task.getId());
        } catch (RuntimeException e) {
            log.warn("FailureClassifier threw exception, fallback to default: taskId={}, reason={}",
                    task.getId(), e.getMessage());
        }
        return defaultFailureClassifier.classify(task, rootCause);
    }

    private String deadOperationType(Throwable error, boolean retriesExhausted) {
        if (retriesExhausted) {
            return "SYSTEM_RETRY_EXHAUSTED";
        }
        if (error instanceof NonRetryableException) {
            return "SYSTEM_NON_RETRYABLE_DEAD";
        }
        return "SYSTEM_FAILURE_CLASSIFIED_DEAD";
    }

    private String appendDecisionReason(String errorMessage, FailureDecision failureDecision) {
        String reason = failureDecision.getReason();
        if (reason == null || reason.isBlank()) {
            return errorMessage;
        }
        return errorMessage + " (decision=" + reason + ")";
    }

    private String deadNotifyReason(Throwable error, String errorMessage, FailureDecision failureDecision) {
        if (error instanceof NonRetryableException) {
            return "non-retryable exception: " + errorMessage;
        }
        return "failure classified as dead: " + appendDecisionReason(errorMessage, failureDecision);
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
                    .errorMsg(truncate(errorMsg, 2000))
                    .traceId(task.getTraceId())
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to record task audit event: taskId={}, operationType={}, reason={}",
                    task.getId(), operationType, e.getMessage());
        }
    }

    /**
     * 递归解包异常，找到最内层的业务异常
     *
     * <p>CompletableFuture 会将异常包装为 CompletionException，
     * ExecutorService 会将异常包装为 ExecutionException，
     * 需要解包到最内层才能正确判断是否为 NonRetryableException。
     */
    private Throwable unwrap(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause != cause.getCause()) {
            if (cause instanceof CompletionException || cause instanceof ExecutionException) {
                cause = cause.getCause();
            } else {
                break;
            }
        }
        return cause;
    }

    /**
     * 截断字符串到指定长度，防止超长异常信息写入失败
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void publishEvent(TaskEventType eventType, TaskInstance task,
                              TaskStatus statusBefore, TaskStatus statusAfter, String reason) {
        eventPublisher.publish(TaskEvent.of(eventType, task, statusBefore, statusAfter, reason));
    }

    private void dispatchDeadLetter(TaskInstance task, String errorCode, String errorMessage,
                                    String reason, boolean retriesExhausted) {
        deadLetterDispatcher.dispatch(DeadLetterContext.builder()
                .task(task)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .reason(reason)
                .retriesExhausted(retriesExhausted)
                .source("RetryEngine")
                .deadAt(LocalDateTime.now())
                .build());
    }
}
