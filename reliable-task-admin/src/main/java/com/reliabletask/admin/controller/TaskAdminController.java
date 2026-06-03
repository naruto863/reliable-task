package com.reliabletask.admin.controller;

import com.reliabletask.admin.metrics.TaskMetricsCollector;
import com.reliabletask.admin.model.Result;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务管理查询接口
 *
 * <p>提供任务列表分页查询、任务详情、执行日志、统计数据等只读接口。
 * 所有接口路径前缀: /api/reliable-task
 */
@RestController
@RequestMapping("/api/reliable-task")
public class TaskAdminController {

    private static final String TASK_VIEW = "TASK_VIEW";
    private static final String TASK_RETRY = "TASK_RETRY";
    private static final String TASK_CANCEL = "TASK_CANCEL";
    private static final String TASK_UPDATE_PAYLOAD = "TASK_UPDATE_PAYLOAD";
    private static final String TASK_BATCH_OPERATION = "TASK_BATCH_OPERATION";
    private static final String TASK_AUDIT_VIEW = "TASK_AUDIT_VIEW";
    private static final int DEFAULT_MAX_PAGE_SIZE = TaskQueryRequest.DEFAULT_MAX_PAGE_SIZE;
    private static final int DEFAULT_MAX_BATCH_LIMIT = 1000;

    private final TaskStore taskStore;
    private final boolean authEnabled;
    private final TaskAuthorizationProvider authorizationProvider;
    private final long staleWorkerThresholdSeconds;
    private final boolean auditEnabled;
    private final boolean batchEnabled;
    private final int maxPageSize;
    private final int maxBatchLimit;
    private final boolean writeEnabled;
    private final TaskEventPublisher eventPublisher;

    public TaskAdminController(TaskStore taskStore) {
        this(taskStore, false, new NoopTaskAuthorizationProvider(), 60L, true, true,
                DEFAULT_MAX_PAGE_SIZE, DEFAULT_MAX_BATCH_LIMIT);
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider) {
        this(taskStore, authEnabled, authorizationProvider, 60L, true, true,
                DEFAULT_MAX_PAGE_SIZE, DEFAULT_MAX_BATCH_LIMIT);
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds) {
        this(taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, true, true,
                DEFAULT_MAX_PAGE_SIZE, DEFAULT_MAX_BATCH_LIMIT);
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled) {
        this(taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, auditEnabled, batchEnabled,
                DEFAULT_MAX_PAGE_SIZE, DEFAULT_MAX_BATCH_LIMIT, true);
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled,
                               int maxPageSize,
                               int maxBatchLimit) {
        this(taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, auditEnabled, batchEnabled,
                maxPageSize, maxBatchLimit, true);
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled,
                               int maxPageSize,
                               int maxBatchLimit,
                               boolean writeEnabled) {
        this(taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, auditEnabled, batchEnabled,
                maxPageSize, maxBatchLimit, writeEnabled, new TaskEventPublisher());
    }

    public TaskAdminController(TaskStore taskStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled,
                               int maxPageSize,
                               int maxBatchLimit,
                               boolean writeEnabled,
                               TaskEventPublisher eventPublisher) {
        this.taskStore = taskStore;
        this.authEnabled = authEnabled;
        this.authorizationProvider = authorizationProvider;
        this.staleWorkerThresholdSeconds = staleWorkerThresholdSeconds;
        this.auditEnabled = auditEnabled;
        this.batchEnabled = batchEnabled;
        this.maxPageSize = normalizeConfiguredMax(maxPageSize, DEFAULT_MAX_PAGE_SIZE);
        this.maxBatchLimit = normalizeConfiguredMax(maxBatchLimit, DEFAULT_MAX_BATCH_LIMIT);
        this.writeEnabled = writeEnabled;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
    }

    /**
     * 分页查询任务列表
     *
     * <p>支持多条件筛选: status、taskType、bizType、bizId、workerId、traceId、tenantId、createTimeStart/End
     */
    @GetMapping("/tasks")
    public Result<PageResult<TaskVO>> listTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) String workerId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) LocalDateTime createTimeStart,
            @RequestParam(required = false) LocalDateTime createTimeEnd,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<PageResult<TaskVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }

        TaskQueryRequest request = TaskQueryRequest.builder()
                .status(status != null ? com.reliabletask.core.enums.TaskStatus.fromCode(status) : null)
                .taskType(taskType)
                .bizType(bizType)
                .bizId(bizId)
                .workerId(workerId)
                .traceId(traceId)
                .tenantId(tenantId)
                .createTimeStart(createTimeStart)
                .createTimeEnd(createTimeEnd)
                .pageNum(normalizePageNum(pageNum))
                .pageSize(normalizePageSize(pageSize))
                .build();

        PageResult<TaskVO> pageResult = taskStore.listTasks(request);
        return Result.success(pageResult);
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/tasks/{id}")
    public Result<TaskDetailVO> getTaskDetail(@PathVariable Long id,
                                              @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<TaskDetailVO> forbidden = forbidIfNeeded(TASK_VIEW, operator, id, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        TaskDetailVO detail = taskStore.getTaskDetail(id);
        if (detail == null) {
            return Result.error(404, "Task not found: " + id);
        }
        return Result.success(detail);
    }

    /**
     * 查询任务执行日志
     */
    @GetMapping("/tasks/{id}/logs")
    public Result<List<TaskLogVO>> getTaskLogs(@PathVariable Long id,
                                               @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<TaskLogVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, id, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        List<TaskLogVO> logs = taskStore.getTaskLogs(id);
        return Result.success(logs);
    }

    /**
     * 查询任务统计数据
     */
    @GetMapping("/tasks/stats")
    public Result<TaskStatsVO> getStats(
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<TaskStatsVO> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        TaskStatsVO stats = taskStore.getStats();
        return Result.success(stats);
    }

    /**
     * 查询 Worker 实例列表。
     */
    @GetMapping("/workers")
    public Result<List<WorkerHeartbeat>> listWorkers(
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<WorkerHeartbeat>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(taskStore.listWorkers());
    }

    /**
     * 查询失联 Worker 实例。
     */
    @GetMapping("/workers/stale")
    public Result<List<WorkerHeartbeat>> listStaleWorkers(
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<WorkerHeartbeat>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        LocalDateTime heartbeatBefore =
                LocalDateTime.now().minusSeconds(Math.max(staleWorkerThresholdSeconds, 1L));
        return Result.success(taskStore.findStaleWorkers(heartbeatBefore));
    }

    /**
     * 手动重试
     *
     * <p>将 DEAD/CANCELLED 状态的任务人工重置为 PENDING，重新进入执行队列。
     * 状态流转: DEAD/CANCELLED → PENDING
     */
    @PostMapping("/tasks/{id}/retry")
    public Result<Boolean> retry(@PathVariable Long id,
                                 @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<Boolean> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<Boolean> forbidden = forbidIfNeeded(TASK_RETRY, operator, id, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskStore.requeueTask(id);
        if (!success) {
            recordAdminAudit("TASK_RETRY", operator, id, "retry task",
                    "FAILED", "Task cannot be retried.", traceId);
            return Result.error(400, "Task cannot be retried. Only DEAD or CANCELLED tasks can be retried.");
        }
        recordAdminAudit("TASK_RETRY", operator, id, "retry task", "SUCCESS", null, traceId);
        publishAdminEvent(TaskEventType.REQUEUED, id, before, statusOf(before),
                TaskStatus.PENDING, "manual retry by " + operator, traceId);
        return Result.success(true);
    }

    /**
     * 终止任务
     *
     * <p>将非终态任务标记为 CANCELLED，终止后不可恢复。
     * 状态流转: PENDING/RUNNING/RETRYING/FAILED → CANCELLED
     */
    @PostMapping("/tasks/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id,
                                  @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<Boolean> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<Boolean> forbidden = forbidIfNeeded(TASK_CANCEL, operator, id, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskStore.cancelTask(id);
        if (!success) {
            recordAdminAudit("TASK_CANCEL", operator, id, "cancel task",
                    "FAILED", "Task cannot be cancelled.", traceId);
            return Result.error(400, "Task cannot be cancelled. Only non-terminal tasks can be cancelled.");
        }
        recordAdminAudit("TASK_CANCEL", operator, id, "cancel task", "SUCCESS", null, traceId);
        publishAdminEvent(TaskEventType.CANCELLED, id, before, statusOf(before),
                TaskStatus.CANCELLED, "manual cancel by " + operator, traceId);
        return Result.success(true);
    }

    /**
     * 重新入队
     *
     * <p>将 DEAD/CANCELLED 状态的任务人工重新放入 PENDING 队列。
     * 同时清空错误信息、执行次数和 workerId。
     * 状态流转: DEAD/CANCELLED → PENDING
     */
    @PostMapping("/tasks/{id}/requeue")
    public Result<Boolean> requeue(@PathVariable Long id,
                                   @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<Boolean> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<Boolean> forbidden = forbidIfNeeded(TASK_RETRY, operator, id, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskStore.requeueTask(id);
        if (!success) {
            recordAdminAudit("TASK_REQUEUE", operator, id, "requeue task",
                    "FAILED", "Task cannot be requeued.", traceId);
            return Result.error(400, "Task cannot be requeued. Only DEAD or CANCELLED tasks can be requeued.");
        }
        recordAdminAudit("TASK_REQUEUE", operator, id, "requeue task", "SUCCESS", null, traceId);
        publishAdminEvent(TaskEventType.REQUEUED, id, before, statusOf(before),
                TaskStatus.PENDING, "manual requeue by " + operator, traceId);
        return Result.success(true);
    }

    /**
     * 修改任务参数
     *
     * <p>仅允许修改 PENDING/RETRYING 状态的任务参数，防止修改正在执行的任务。
     * 请求体: {"payload": "..."}
     */
    @PutMapping("/tasks/{id}/payload")
    public Result<Boolean> updatePayload(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<Boolean> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<Boolean> forbidden = forbidIfNeeded(TASK_UPDATE_PAYLOAD, operator, id, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        String payload = body.get("payload");
        if (payload == null) {
            recordAdminAudit("TASK_UPDATE_PAYLOAD", operator, id, "update payload",
                    "FAILED", "payload is required", traceId);
            return Result.error(400, "payload is required");
        }

        boolean success = taskStore.updatePayload(id, payload);
        if (!success) {
            recordAdminAudit("TASK_UPDATE_PAYLOAD", operator, id, "update payload",
                    "FAILED", "Task payload can only be updated for PENDING or RETRYING tasks.", traceId);
            return Result.error(400, "Task payload can only be updated for PENDING or RETRYING tasks.");
        }
        recordAdminAudit("TASK_UPDATE_PAYLOAD", operator, id, "update payload",
                "SUCCESS", null, traceId);
        return Result.success(true);
    }

    /**
     * 按任务 ID 查询审计日志。
     */
    @GetMapping("/tasks/{id}/audit-logs")
    public Result<List<AuditLog>> getAuditLogsByTaskId(@PathVariable Long id,
                                                       @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        if (!auditEnabled) {
            return Result.error(404, "Audit log is disabled");
        }
        Result<List<AuditLog>> forbidden = forbidIfNeeded(TASK_AUDIT_VIEW, operator, id, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(taskStore.getAuditLogsByTaskId(id));
    }

    /**
     * 分页查询审计日志。
     */
    @GetMapping("/audit-logs")
    public Result<PageResult<AuditLog>> listAuditLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) LocalDateTime createTimeStart,
            @RequestParam(required = false) LocalDateTime createTimeEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String currentOperator) {
        if (!auditEnabled) {
            return Result.error(404, "Audit log is disabled");
        }
        Result<PageResult<AuditLog>> forbidden =
                forbidIfNeeded(TASK_AUDIT_VIEW, currentOperator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(taskStore.listAuditLogs(operator, createTimeStart, createTimeEnd,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    /**
     * 批量操作预览。
     */
    @PostMapping("/tasks/batch/preview")
    public Result<BatchOperationResult> previewBatch(@RequestBody BatchOperationRequest request,
                                                     @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<BatchOperationResult> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
        }
        Result<BatchOperationResult> forbidden =
                forbidIfNeeded(TASK_BATCH_OPERATION, operator, null, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }

        List<Long> taskIds = findBatchTaskIds(request, normalizeLimit(request.limit()));
        Long batchId = createBatchRecord("PREVIEW", operator, request, true, traceId);
        BatchOperationResult result = BatchOperationResult.builder()
                .batchOperationId(batchId)
                .totalCount(taskIds.size())
                .successCount(0)
                .failCount(0)
                .failedTaskIds(List.of())
                .dryRun(true)
                .success(true)
                .build();
        taskStore.updateBatchOperationResult(result);
        recordBatchAudit("BATCH_PREVIEW", operator, batchId, requestSummary(request),
                "SUCCESS", null, traceId);
        return Result.success(result);
    }

    /**
     * 批量重新入队 DEAD 任务。
     */
    @PostMapping("/tasks/batch/requeue")
    public Result<BatchOperationResult> batchRequeue(@RequestBody BatchOperationRequest request,
                                                     @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<BatchOperationResult> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
        }
        Result<BatchOperationResult> forbidden =
                forbidIfNeeded(TASK_BATCH_OPERATION, operator, null, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        BatchOperationRequest effective = request.withStatus(TaskStatus.DEAD.getCode());
        List<Long> taskIds = findBatchTaskIds(effective, normalizeLimit(effective.limit()));
        Long batchId = createBatchRecord("REQUEUE_DEAD", operator, effective, false, traceId);
        BatchOperationResult result = executeBatch(batchId, taskIds, taskStore::requeueTask, false);
        taskStore.updateBatchOperationResult(result);
        recordBatchAudit("BATCH_REQUEUE_DEAD", operator, batchId, requestSummary(effective),
                result.isSuccess() ? "SUCCESS" : "FAILED", result.getErrorMsg(), traceId);
        publishBatchEvents(TaskEventType.REQUEUED, taskIds, result.getFailedTaskIds(),
                TaskStatus.DEAD, TaskStatus.PENDING, "batch requeue by " + operator, traceId);
        return Result.success(result);
    }

    /**
     * 批量取消 PENDING/RETRYING 任务。
     */
    @PostMapping("/tasks/batch/cancel")
    public Result<BatchOperationResult> batchCancel(@RequestBody BatchOperationRequest request,
                                                    @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Result<BatchOperationResult> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
        }
        Result<BatchOperationResult> forbidden =
                forbidIfNeeded(TASK_BATCH_OPERATION, operator, null, true, traceId);
        if (forbidden != null) {
            return forbidden;
        }
        int limit = normalizeLimit(request.limit());
        List<Long> taskIds = findCancelableTaskIds(request, limit);
        Long batchId = createBatchRecord("CANCEL_PENDING_RETRYING", operator, request, false, traceId);
        BatchOperationResult result = executeBatch(batchId, taskIds, taskStore::cancelTask, false);
        taskStore.updateBatchOperationResult(result);
        recordBatchAudit("BATCH_CANCEL", operator, batchId, requestSummary(request),
                result.isSuccess() ? "SUCCESS" : "FAILED", result.getErrorMsg(), traceId);
        publishBatchEvents(TaskEventType.CANCELLED, taskIds, result.getFailedTaskIds(),
                request.status() == null ? null : TaskStatus.fromCode(request.status()),
                TaskStatus.CANCELLED, "batch cancel by " + operator, traceId);
        return Result.success(result);
    }

    private <T> Result<T> forbidIfNeeded(String action, String operator, Long taskId,
                                         boolean writeOperation, String traceId) {
        if (!authEnabled) {
            return null;
        }
        if (authorizationProvider == null) {
            if (!writeOperation) {
                return null;
            }
            recordAdminAudit("AUTH_DENIED", operator, taskId, "action=" + action,
                    "FAILED", "authorization provider is not configured", traceId);
            return Result.error(403, "Forbidden: " + action);
        }
        if (!authorizationProvider.isAllowed(operator, action, taskId)) {
            recordAdminAudit("AUTH_DENIED", operator, taskId, "action=" + action,
                    "FAILED", "permission denied", traceId);
            return Result.error(403, "Forbidden: " + action);
        }
        return null;
    }

    private <T> Result<T> rejectWriteIfDisabled() {
        if (writeEnabled) {
            return null;
        }
        return Result.error(404, "Admin write operation is disabled");
    }

    private void recordAdminAudit(String operationType, String operator, Long taskId,
                                  String requestSummary, String result, String errorMsg, String traceId) {
        if (!auditEnabled) {
            return;
        }
        taskStore.saveAuditLog(AuditLog.builder()
                .operationType(operationType)
                .operator(operator)
                .targetType("TASK")
                .targetId(taskId == null ? null : String.valueOf(taskId))
                .taskId(taskId)
                .requestSummary(requestSummary)
                .result(result)
                .errorMsg(errorMsg)
                .traceId(traceId)
                .createTime(LocalDateTime.now())
                .build());
    }

    private void recordBatchAudit(String operationType, String operator, Long batchId,
                                  String requestSummary, String result, String errorMsg, String traceId) {
        if (!auditEnabled) {
            return;
        }
        taskStore.saveAuditLog(AuditLog.builder()
                .operationType(operationType)
                .operator(operator)
                .targetType("BATCH_OPERATION")
                .targetId(batchId == null ? null : String.valueOf(batchId))
                .batchOperationId(batchId)
                .requestSummary(requestSummary)
                .result(result)
                .errorMsg(errorMsg)
                .traceId(traceId)
                .createTime(LocalDateTime.now())
                .build());
    }

    private Long createBatchRecord(String operationType, String operator, BatchOperationRequest request,
                                   boolean dryRun, String traceId) {
        return taskStore.createBatchOperation(operationType, operator, request.taskType(),
                request.status() == null ? null : TaskStatus.fromCode(request.status()),
                request.createTimeStart(), request.createTimeEnd(), normalizeLimit(request.limit()),
                dryRun, requestSummary(request), traceId);
    }

    private List<Long> findBatchTaskIds(BatchOperationRequest request, int limit) {
        return taskStore.findOperableTaskIds(request.taskType(),
                request.status() == null ? null : TaskStatus.fromCode(request.status()),
                request.createTimeStart(), request.createTimeEnd(), limit);
    }

    private List<Long> findCancelableTaskIds(BatchOperationRequest request, int limit) {
        if (request.status() != null) {
            TaskStatus status = TaskStatus.fromCode(request.status());
            if (status != TaskStatus.PENDING && status != TaskStatus.RETRYING) {
                return List.of();
            }
            return findBatchTaskIds(request, limit);
        }
        List<Long> pendingIds = taskStore.findOperableTaskIds(request.taskType(), TaskStatus.PENDING,
                request.createTimeStart(), request.createTimeEnd(), limit);
        if (pendingIds.size() >= limit) {
            return pendingIds;
        }
        List<Long> retryingIds = taskStore.findOperableTaskIds(request.taskType(), TaskStatus.RETRYING,
                request.createTimeStart(), request.createTimeEnd(), limit - pendingIds.size());
        return java.util.stream.Stream.concat(pendingIds.stream(), retryingIds.stream()).toList();
    }

    private BatchOperationResult executeBatch(Long batchId, List<Long> taskIds,
                                              java.util.function.Function<Long, Boolean> operation,
                                              boolean dryRun) {
        int successCount = 0;
        List<Long> failedTaskIds = new java.util.ArrayList<>();
        if (!dryRun) {
            for (Long taskId : taskIds) {
                try {
                    if (Boolean.TRUE.equals(operation.apply(taskId))) {
                        successCount++;
                    } else {
                        failedTaskIds.add(taskId);
                    }
                } catch (RuntimeException e) {
                    failedTaskIds.add(taskId);
                }
            }
        }
        return BatchOperationResult.builder()
                .batchOperationId(batchId)
                .totalCount(taskIds.size())
                .successCount(successCount)
                .failCount(failedTaskIds.size())
                .failedTaskIds(failedTaskIds)
                .failedSummary(failedTaskIds.toString())
                .dryRun(dryRun)
                .success(failedTaskIds.isEmpty())
                .errorMsg(failedTaskIds.isEmpty() ? null : "Some tasks failed")
                .build();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 100;
        }
        return Math.min(limit, maxBatchLimit);
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? TaskQueryRequest.DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return Math.min(TaskQueryRequest.DEFAULT_PAGE_SIZE, maxPageSize);
        }
        return Math.min(pageSize, maxPageSize);
    }

    private int normalizeConfiguredMax(int configuredMax, int hardMax) {
        if (configuredMax <= 0) {
            return hardMax;
        }
        return Math.min(configuredMax, hardMax);
    }

    private String requestSummary(BatchOperationRequest request) {
        return "taskType=" + request.taskType()
                + ", status=" + request.status()
                + ", limit=" + normalizeLimit(request.limit())
                + ", dryRun=" + request.dryRun();
    }

    private TaskInstance loadTaskForEvent(Long taskId) {
        if (taskId == null) {
            return null;
        }
        try {
            return taskStore.getById(taskId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TaskStatus statusOf(TaskInstance task) {
        return task == null ? null : task.getStatus();
    }

    private void publishAdminEvent(TaskEventType eventType, Long taskId, TaskInstance task,
                                   TaskStatus statusBefore, TaskStatus statusAfter,
                                   String reason, String traceId) {
        TaskEvent.TaskEventBuilder builder = TaskEvent.builder()
                .eventType(eventType)
                .taskId(taskId)
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .reason(reason)
                .traceId(traceId)
                .eventTime(LocalDateTime.now());
        if (task != null) {
            builder.taskType(task.getTaskType())
                    .bizType(task.getBizType())
                    .bizId(task.getBizId())
                    .workerId(task.getWorkerId());
            if (traceId == null || traceId.isBlank()) {
                builder.traceId(task.getTraceId());
            }
        }
        eventPublisher.publish(builder.build());
    }

    private void publishBatchEvents(TaskEventType eventType, List<Long> taskIds, List<Long> failedTaskIds,
                                    TaskStatus statusBefore, TaskStatus statusAfter,
                                    String reason, String traceId) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        List<Long> failed = failedTaskIds == null ? List.of() : failedTaskIds;
        for (Long taskId : taskIds) {
            if (!failed.contains(taskId)) {
                publishAdminEvent(eventType, taskId, null, statusBefore, statusAfter, reason, traceId);
            }
        }
    }

    public record BatchOperationRequest(String taskType,
                                        Integer status,
                                        LocalDateTime createTimeStart,
                                        LocalDateTime createTimeEnd,
                                        Integer limit,
                                        Boolean dryRun) {
        BatchOperationRequest withStatus(Integer newStatus) {
            return new BatchOperationRequest(taskType, newStatus, createTimeStart, createTimeEnd, limit, dryRun);
        }
    }
}
