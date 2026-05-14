package com.reliabletask.executor.retry;

import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.diagnostics.DefaultTaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskFailureDiagnostic;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.spi.RetryStrategy;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.strategy.DefaultRetryStrategyFactory;
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
    private final TaskStore taskStore;
    private final TaskMetricsRecorder metricsRecorder;
    private final TaskAuditRecorder auditRecorder;
    private final TaskAlertService alertService;
    private final TaskExceptionFormatter exceptionFormatter;

    public RetryEngine(TaskStore taskStore) {
        this(taskStore, new NoopTaskMetricsRecorder(), new NoopTaskAuditRecorder());
    }

    public RetryEngine(TaskStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder) {
        this(taskStore, metricsRecorder, auditRecorder, new NoopTaskAlertService());
    }

    public RetryEngine(TaskStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService) {
        this(taskStore, metricsRecorder, auditRecorder, alertService, new DefaultTaskExceptionFormatter());
    }

    public RetryEngine(TaskStore taskStore,
                       TaskMetricsRecorder metricsRecorder,
                       TaskAuditRecorder auditRecorder,
                       TaskAlertService alertService,
                       TaskExceptionFormatter exceptionFormatter) {
        this.taskStore = taskStore;
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new NoopTaskMetricsRecorder();
        this.auditRecorder = auditRecorder != null ? auditRecorder : new NoopTaskAuditRecorder();
        this.alertService = alertService != null ? alertService : new NoopTaskAlertService();
        this.exceptionFormatter = exceptionFormatter != null
                ? exceptionFormatter
                : new DefaultTaskExceptionFormatter();
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
                RetryStrategyResolver.resolve(handler, task);

        boolean isNonRetryable = rootCause instanceof NonRetryableException;
        boolean retriesExhausted = task.getActualRetryCount() >= config.getMaxRetryCount();

        if (isNonRetryable || retriesExhausted) {
            markDead(task, rootCause, durationMs, statusBefore, retriesExhausted);
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
                          String statusBefore, boolean retriesExhausted) {
        TaskFailureDiagnostic diagnostic = exceptionFormatter.format(error);
        String errorCode = diagnostic.getErrorCode();
        String errorMessage = diagnostic.getErrorMessage();
        String logMessage = diagnostic.getStackTrace() != null ? diagnostic.getStackTrace() : errorMessage;
        taskStore.markDead(task.getId(), errorCode, truncate(errorMessage, 2000));
        taskStore.saveLog(task.getId(), task.getExecuteCount(),
                statusBefore, "DEAD", false, durationMs,
                errorCode, truncate(logMessage, 4000),
                task.getWorkerId(), task.getTraceId());
        log.warn("Task marked DEAD: id={}, type={}, bizId={}, reason={}, exhausted={}",
                task.getId(), task.getTaskType(), task.getBizId(),
                errorMessage, retriesExhausted);
        recordMetrics(task, TaskStatus.DEAD, durationMs, false, errorCode);
        recordAudit(task,
                retriesExhausted ? "SYSTEM_RETRY_EXHAUSTED" : "SYSTEM_NON_RETRYABLE_DEAD",
                "SUCCESS",
                errorMessage);
        notifyDead(task, retriesExhausted ? "max retry exceeded: " + errorMessage
                : "non-retryable exception: " + errorMessage);
    }

    /**
     * 标记任务为 RETRYING 状态
     *
     * <p>状态流转: RUNNING → RETRYING
     * 通过重试策略计算下次执行时间，同时记录执行日志。
     */
    private void markWaitRetry(TaskInstance task, Throwable error, long durationMs,
                               String statusBefore, RetryStrategyResolver.ResolvedRetryConfig config) {
        RetryStrategy strategy = DefaultRetryStrategyFactory.getStrategy(config.getStrategy());
        long delayMs = strategy.nextDelayMs(
                task.getActualRetryCount(),
                config.getRetryIntervalMs(),
                config.getMaxDelayMs()
        );
        LocalDateTime nextExecuteTime = LocalDateTime.now().plus(Duration.ofMillis(delayMs));

        TaskFailureDiagnostic diagnostic = exceptionFormatter.format(error);
        String errorCode = diagnostic.getErrorCode();
        String errorMessage = diagnostic.getErrorMessage();
        String logMessage = diagnostic.getStackTrace() != null ? diagnostic.getStackTrace() : errorMessage;
        taskStore.markWaitRetry(task.getId(), errorCode, truncate(errorMessage, 2000), nextExecuteTime);
        taskStore.saveLog(task.getId(), task.getExecuteCount(),
                statusBefore, "RETRYING", false, durationMs,
                errorCode, truncate(logMessage, 4000),
                task.getWorkerId(), task.getTraceId());
        log.info("Task marked RETRYING: id={}, type={}, bizId={}, nextExecuteTime={}, delay={}ms",
                task.getId(), task.getTaskType(), task.getBizId(), nextExecuteTime, delayMs);
        recordMetrics(task, TaskStatus.RETRYING, durationMs, false, errorCode);
        recordAudit(task, "SYSTEM_RETRY_SCHEDULED", "SUCCESS", errorMessage);
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
}
