package com.reliabletask.admin.controller;

import com.reliabletask.admin.metrics.TaskMetricsCollector;
import com.reliabletask.admin.model.Result;
import com.reliabletask.core.dto.FailureTopQueryRequest;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.SlowTaskQueryRequest;
import com.reliabletask.core.dto.TaskFailureQueryRequest;
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
import com.reliabletask.core.spi.TaskOperationsStore;
import com.reliabletask.core.spi.TaskQueryStore;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import com.reliabletask.core.vo.ConsoleCapabilitiesVO;
import com.reliabletask.core.vo.ConsoleTaskDetailVO;
import com.reliabletask.core.vo.PayloadViewVO;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.FailureTopVO;
import com.reliabletask.core.vo.SlowTaskVO;
import com.reliabletask.core.vo.TaskFailureVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskTimelineItemVO;
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
    private static final int DEFAULT_FAILURE_TOP_LIMIT = 20;
    private static final int HARD_MAX_FAILURE_TOP_LIMIT = 100;
    private static final int DEFAULT_PAYLOAD_PREVIEW_LENGTH = 512;

    private final TaskQueryStore taskQueryStore;
    private final TaskOperationsStore taskOperationsStore;
    private final boolean authEnabled;
    private final TaskAuthorizationProvider authorizationProvider;
    private final long staleWorkerThresholdSeconds;
    private final boolean auditEnabled;
    private final boolean batchEnabled;
    private final int maxPageSize;
    private final int maxBatchLimit;
    private final boolean writeEnabled;
    private final TaskEventPublisher eventPublisher;
    private final AdminQueryGuard adminQueryGuard;
    private final boolean payloadPlaintextEnabled;
    private final int payloadPreviewLength;
    private final boolean writeConfirmationRequired;

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
        this(taskStore, taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, auditEnabled, batchEnabled,
                maxPageSize, maxBatchLimit, writeEnabled, eventPublisher, AdminQueryGuard.defaults());
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
                               TaskEventPublisher eventPublisher,
                               AdminQueryGuard adminQueryGuard,
                               boolean payloadPlaintextEnabled,
                               int payloadPreviewLength,
                               boolean writeConfirmationRequired) {
        this(taskStore, taskStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds, auditEnabled, batchEnabled,
                maxPageSize, maxBatchLimit, writeEnabled, eventPublisher, adminQueryGuard,
                payloadPlaintextEnabled, payloadPreviewLength, writeConfirmationRequired);
    }

    public TaskAdminController(TaskQueryStore taskQueryStore,
                               TaskOperationsStore taskOperationsStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled,
                               int maxPageSize,
                               int maxBatchLimit,
                               boolean writeEnabled,
                               TaskEventPublisher eventPublisher,
                               AdminQueryGuard adminQueryGuard) {
        this(taskQueryStore, taskOperationsStore, authEnabled, authorizationProvider, staleWorkerThresholdSeconds,
                auditEnabled, batchEnabled, maxPageSize, maxBatchLimit, writeEnabled, eventPublisher, adminQueryGuard,
                false, DEFAULT_PAYLOAD_PREVIEW_LENGTH, true);
    }

    public TaskAdminController(TaskQueryStore taskQueryStore,
                               TaskOperationsStore taskOperationsStore,
                               boolean authEnabled,
                               TaskAuthorizationProvider authorizationProvider,
                               long staleWorkerThresholdSeconds,
                               boolean auditEnabled,
                               boolean batchEnabled,
                               int maxPageSize,
                               int maxBatchLimit,
                               boolean writeEnabled,
                               TaskEventPublisher eventPublisher,
                               AdminQueryGuard adminQueryGuard,
                               boolean payloadPlaintextEnabled,
                               int payloadPreviewLength,
                               boolean writeConfirmationRequired) {
        this.taskQueryStore = taskQueryStore;
        this.taskOperationsStore = taskOperationsStore;
        this.authEnabled = authEnabled;
        this.authorizationProvider = authorizationProvider;
        this.staleWorkerThresholdSeconds = staleWorkerThresholdSeconds;
        this.auditEnabled = auditEnabled;
        this.batchEnabled = batchEnabled;
        this.maxPageSize = normalizeConfiguredMax(maxPageSize, DEFAULT_MAX_PAGE_SIZE);
        this.maxBatchLimit = normalizeConfiguredMax(maxBatchLimit, DEFAULT_MAX_BATCH_LIMIT);
        this.writeEnabled = writeEnabled;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
        this.adminQueryGuard = adminQueryGuard != null ? adminQueryGuard : AdminQueryGuard.defaults();
        this.payloadPlaintextEnabled = payloadPlaintextEnabled;
        this.payloadPreviewLength = normalizePayloadPreviewLength(payloadPreviewLength);
        this.writeConfirmationRequired = writeConfirmationRequired;
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

        PageResult<TaskVO> pageResult = taskQueryStore.listTasks(request);
        return Result.success(pageResult);
    }

    /**
     * 查询最近失败执行记录。
     */
    @GetMapping("/tasks/recent-failures")
    public Result<List<TaskFailureVO>> listRecentFailures(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) LocalDateTime createTimeStart,
            @RequestParam(required = false) LocalDateTime createTimeEnd,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<TaskFailureVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }

        AdminQueryGuard.NormalizedQuery normalized;
        try {
            normalized = normalizeOperationalQuery(createTimeStart, createTimeEnd, limit, null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }

        TaskFailureQueryRequest request = TaskFailureQueryRequest.builder()
                .taskType(taskType)
                .errorCode(errorCode)
                .createTimeStart(normalized.createTimeStart())
                .createTimeEnd(normalized.createTimeEnd())
                .limit(normalized.limit())
                .build();
        return Result.success(taskQueryStore.listRecentFailures(request));
    }

    /**
     * 查询慢执行任务记录。
     */
    @GetMapping("/tasks/slow")
    public Result<List<SlowTaskVO>> listSlowTasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long durationMsGte,
            @RequestParam(required = false) LocalDateTime createTimeStart,
            @RequestParam(required = false) LocalDateTime createTimeEnd,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<SlowTaskVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }

        AdminQueryGuard.NormalizedQuery normalized;
        try {
            normalized = normalizeOperationalQuery(createTimeStart, createTimeEnd, limit, null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }

        SlowTaskQueryRequest request = SlowTaskQueryRequest.builder()
                .taskType(taskType)
                .durationMsGte(normalizeSlowThresholdMs(durationMsGte))
                .createTimeStart(normalized.createTimeStart())
                .createTimeEnd(normalized.createTimeEnd())
                .limit(normalized.limit())
                .build();
        return Result.success(taskQueryStore.listSlowTasks(request));
    }

    /**
     * 查询失败 Top 聚合。
     */
    @GetMapping("/tasks/failure-top")
    public Result<List<FailureTopVO>> listFailureTop(
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) LocalDateTime createTimeStart,
            @RequestParam(required = false) LocalDateTime createTimeEnd,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<List<FailureTopVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }

        String normalizedGroupBy;
        AdminQueryGuard.NormalizedQuery normalized;
        try {
            normalizedGroupBy = normalizeFailureTopGroupBy(groupBy);
            normalized = normalizeOperationalQuery(createTimeStart, createTimeEnd,
                    normalizeFailureTopLimit(limit), null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }

        FailureTopQueryRequest request = FailureTopQueryRequest.builder()
                .groupBy(normalizedGroupBy)
                .taskType(taskType)
                .createTimeStart(normalized.createTimeStart())
                .createTimeEnd(normalized.createTimeEnd())
                .limit(normalized.limit())
                .build();
        return Result.success(taskQueryStore.listFailureTop(request));
    }

    /**
     * 查询控制台能力开关。
     */
    @GetMapping("/console/capabilities")
    public Result<ConsoleCapabilitiesVO> getConsoleCapabilities(
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<ConsoleCapabilitiesVO> forbidden = forbidIfNeeded(TASK_VIEW, operator, null, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        return Result.success(buildConsoleCapabilities());
    }

    /**
     * 查询控制台安全任务详情。
     */
    @GetMapping("/console/tasks/{id}")
    public Result<ConsoleTaskDetailVO> getConsoleTaskDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator) {
        Result<ConsoleTaskDetailVO> forbidden = forbidIfNeeded(TASK_VIEW, operator, id, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        TaskDetailVO detail = taskQueryStore.getTaskDetail(id);
        if (detail == null) {
            return Result.error(404, "Task not found: " + id);
        }
        return Result.success(toConsoleTaskDetail(detail));
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
        TaskDetailVO detail = taskQueryStore.getTaskDetail(id);
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
        List<TaskLogVO> logs = taskQueryStore.getTaskLogs(id);
        return Result.success(logs);
    }

    /**
     * 查询任务生命周期时间线
     */
    @GetMapping("/tasks/{id}/timeline")
    public Result<List<TaskTimelineItemVO>> getTaskTimeline(@PathVariable Long id,
                                                            @RequestHeader(value = "X-Operator", defaultValue = "anonymous")
                                                            String operator) {
        Result<List<TaskTimelineItemVO>> forbidden = forbidIfNeeded(TASK_VIEW, operator, id, false, null);
        if (forbidden != null) {
            return forbidden;
        }
        TaskDetailVO detail = taskQueryStore.getTaskDetail(id);
        if (detail == null) {
            return Result.error(404, "Task not found: " + id);
        }
        return Result.success(taskQueryStore.getTaskTimeline(id));
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
        TaskStatsVO stats = taskQueryStore.getStats();
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
        return Result.success(taskOperationsStore.listWorkers());
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
        return Result.success(taskOperationsStore.findStaleWorkers(heartbeatBefore));
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
                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                 @RequestHeader(value = "X-Confirm-Operation", required = false)
                                 String confirmOperation) {
        Result<Boolean> rejected =
                rejectTaskWriteIfNotAllowed("TASK_RETRY", TASK_RETRY, operator, id, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskOperationsStore.requeueTask(id);
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
                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                  @RequestHeader(value = "X-Confirm-Operation", required = false)
                                  String confirmOperation) {
        Result<Boolean> rejected =
                rejectTaskWriteIfNotAllowed("TASK_CANCEL", TASK_CANCEL, operator, id, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskOperationsStore.cancelTask(id);
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
                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                   @RequestHeader(value = "X-Confirm-Operation", required = false)
                                   String confirmOperation) {
        Result<Boolean> rejected =
                rejectTaskWriteIfNotAllowed("TASK_REQUEUE", TASK_RETRY, operator, id, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        TaskInstance before = loadTaskForEvent(id);
        boolean success = taskOperationsStore.requeueTask(id);
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
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                         @RequestHeader(value = "X-Confirm-Operation", required = false)
                                         String confirmOperation) {
        Result<Boolean> rejected = rejectTaskWriteIfNotAllowed(
                "TASK_UPDATE_PAYLOAD", TASK_UPDATE_PAYLOAD, operator, id, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        String payload = body.get("payload");
        if (payload == null) {
            recordAdminAudit("TASK_UPDATE_PAYLOAD", operator, id, "update payload",
                    "FAILED", "payload is required", traceId);
            return Result.error(400, "payload is required");
        }

        boolean success = taskOperationsStore.updatePayload(id, payload);
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
        return Result.success(taskOperationsStore.getAuditLogsByTaskId(id));
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
        return Result.success(taskOperationsStore.listAuditLogs(operator, createTimeStart, createTimeEnd,
                normalizePageNum(pageNum), normalizePageSize(pageSize)));
    }

    /**
     * 批量操作预览。
     */
    @PostMapping("/tasks/batch/preview")
    public Result<BatchOperationResult> previewBatch(@RequestBody BatchOperationRequest request,
                                                     @RequestHeader(value = "X-Operator", defaultValue = "anonymous") String operator,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                     @RequestHeader(value = "X-Confirm-Operation", required = false)
                                                     String confirmOperation) {
        Result<BatchOperationResult> rejected = rejectBatchWriteIfNotAllowed(
                "BATCH_PREVIEW", operator, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
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
        taskOperationsStore.updateBatchOperationResult(result);
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
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                     @RequestHeader(value = "X-Confirm-Operation", required = false)
                                                     String confirmOperation) {
        Result<BatchOperationResult> rejected = rejectBatchWriteIfNotAllowed(
                "BATCH_REQUEUE_DEAD", operator, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
        }
        BatchOperationRequest effective = request.withStatus(TaskStatus.DEAD.getCode());
        List<Long> taskIds = findBatchTaskIds(effective, normalizeLimit(effective.limit()));
        Long batchId = createBatchRecord("REQUEUE_DEAD", operator, effective, false, traceId);
        BatchOperationResult result = executeBatch(batchId, taskIds, taskOperationsStore::requeueTask, false);
        taskOperationsStore.updateBatchOperationResult(result);
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
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                    @RequestHeader(value = "X-Confirm-Operation", required = false)
                                                    String confirmOperation) {
        Result<BatchOperationResult> rejected = rejectBatchWriteIfNotAllowed(
                "BATCH_CANCEL", operator, traceId, confirmOperation);
        if (rejected != null) {
            return rejected;
        }
        if (!batchEnabled) {
            return Result.error(404, "Batch operation is disabled");
        }
        int limit = normalizeLimit(request.limit());
        List<Long> taskIds = findCancelableTaskIds(request, limit);
        Long batchId = createBatchRecord("CANCEL_PENDING_RETRYING", operator, request, false, traceId);
        BatchOperationResult result = executeBatch(batchId, taskIds, taskOperationsStore::cancelTask, false);
        taskOperationsStore.updateBatchOperationResult(result);
        recordBatchAudit("BATCH_CANCEL", operator, batchId, requestSummary(request),
                result.isSuccess() ? "SUCCESS" : "FAILED", result.getErrorMsg(), traceId);
        publishBatchEvents(TaskEventType.CANCELLED, taskIds, result.getFailedTaskIds(),
                request.status() == null ? null : TaskStatus.fromCode(request.status()),
                TaskStatus.CANCELLED, "batch cancel by " + operator, traceId);
        return Result.success(result);
    }

    private ConsoleCapabilitiesVO buildConsoleCapabilities() {
        ConsoleCapabilitiesVO capabilities = new ConsoleCapabilitiesVO();
        capabilities.setAdminEnabled(true);
        capabilities.setWriteEnabled(writeEnabled);
        capabilities.setAuthEnabled(authEnabled);
        capabilities.setAuditEnabled(auditEnabled);
        capabilities.setBatchEnabled(batchEnabled);
        capabilities.setMaxPageSize(maxPageSize);
        capabilities.setMaxBatchLimit(maxBatchLimit);
        capabilities.setPayloadPlaintextEnabled(payloadPlaintextEnabled);
        capabilities.setPayloadRevealAllowed(payloadPlaintextEnabled);
        capabilities.setPayloadPreviewLength(payloadPreviewLength);
        capabilities.setWriteConfirmationRequired(writeConfirmationRequired);
        return capabilities;
    }

    private ConsoleTaskDetailVO toConsoleTaskDetail(TaskDetailVO detail) {
        ConsoleTaskDetailVO consoleDetail = new ConsoleTaskDetailVO();
        consoleDetail.setId(detail.getId());
        consoleDetail.setTaskType(detail.getTaskType());
        consoleDetail.setBizType(detail.getBizType());
        consoleDetail.setBizId(detail.getBizId());
        consoleDetail.setBizUniqueKey(detail.getBizUniqueKey());
        consoleDetail.setStatusCode(detail.getStatusCode());
        consoleDetail.setStatusDesc(detail.getStatusDesc());
        consoleDetail.setPriority(detail.getPriority());
        consoleDetail.setPayloadView(buildPayloadView(detail.getPayload()));
        consoleDetail.setExecuteCount(detail.getExecuteCount());
        consoleDetail.setMaxRetryCount(detail.getMaxRetryCount());
        consoleDetail.setRetryStrategy(detail.getRetryStrategy());
        consoleDetail.setRetryIntervalMs(detail.getRetryIntervalMs());
        consoleDetail.setNextExecuteTime(detail.getNextExecuteTime());
        consoleDetail.setShardKey(detail.getShardKey());
        consoleDetail.setTenantId(detail.getTenantId());
        consoleDetail.setWorkerId(detail.getWorkerId());
        consoleDetail.setErrorMsg(detail.getErrorMsg());
        consoleDetail.setTraceId(detail.getTraceId());
        consoleDetail.setCreateTime(detail.getCreateTime());
        consoleDetail.setUpdateTime(detail.getUpdateTime());
        consoleDetail.setFinishTime(detail.getFinishTime());
        return consoleDetail;
    }

    private PayloadViewVO buildPayloadView(String payload) {
        PayloadViewVO view = new PayloadViewVO();
        boolean hasPayload = payload != null;
        view.setPayloadVisible(hasPayload);
        view.setPayloadLength(hasPayload ? payload.length() : 0);
        view.setPayloadRevealAllowed(hasPayload && payloadPlaintextEnabled);
        if (!hasPayload) {
            return view;
        }

        view.setPayloadMasked(!payloadPlaintextEnabled);
        view.setPayloadPreview(payloadPlaintextEnabled
                ? truncatePayloadPreview(payload)
                : maskPayloadPreview(payload));
        if (payloadPlaintextEnabled) {
            view.setPayloadPlaintext(payload);
        }
        return view;
    }

    private String maskPayloadPreview(String payload) {
        int previewLength = Math.min(payload.length(), payloadPreviewLength);
        String preview = "*".repeat(previewLength);
        if (payload.length() > previewLength) {
            return preview + "...(truncated)";
        }
        return preview;
    }

    private String truncatePayloadPreview(String payload) {
        if (payload.length() <= payloadPreviewLength) {
            return payload;
        }
        return payload.substring(0, payloadPreviewLength) + "...(truncated)";
    }

    private <T> Result<T> rejectTaskWriteIfNotAllowed(String operationType, String action, String operator,
                                                       Long taskId, String traceId, String confirmOperation) {
        Result<T> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<T> unsafe = rejectUnsafeTaskWriteConfig(operationType, operator, taskId, traceId);
        if (unsafe != null) {
            return unsafe;
        }
        Result<T> unconfirmed =
                rejectTaskWriteIfConfirmationMissing(operationType, operator, taskId, traceId, confirmOperation);
        if (unconfirmed != null) {
            return unconfirmed;
        }
        return forbidIfNeeded(action, operator, taskId, true, traceId);
    }

    private <T> Result<T> rejectBatchWriteIfNotAllowed(String operationType, String operator,
                                                       String traceId, String confirmOperation) {
        Result<T> disabled = rejectWriteIfDisabled();
        if (disabled != null) {
            return disabled;
        }
        Result<T> unsafe = rejectUnsafeBatchWriteConfig(operationType, operator, traceId);
        if (unsafe != null) {
            return unsafe;
        }
        Result<T> unconfirmed =
                rejectBatchWriteIfConfirmationMissing(operationType, operator, traceId, confirmOperation);
        if (unconfirmed != null) {
            return unconfirmed;
        }
        return forbidIfNeeded(TASK_BATCH_OPERATION, operator, null, true, traceId);
    }

    private <T> Result<T> rejectUnsafeTaskWriteConfig(String operationType, String operator,
                                                      Long taskId, String traceId) {
        if (!authEnabled) {
            String message = "Admin write operation requires reliable-task.admin.auth.enabled=true";
            recordAdminAudit(operationType, operator, taskId, "write precondition", "FAILED", message, traceId);
            return Result.error(403, message);
        }
        if (!auditEnabled) {
            return Result.error(403,
                    "Admin write operation requires reliable-task.admin.audit.enabled=true");
        }
        return null;
    }

    private <T> Result<T> rejectUnsafeBatchWriteConfig(String operationType, String operator, String traceId) {
        if (!authEnabled) {
            String message = "Admin write operation requires reliable-task.admin.auth.enabled=true";
            recordBatchAudit(operationType, operator, null, "write precondition", "FAILED", message, traceId);
            return Result.error(403, message);
        }
        if (!auditEnabled) {
            return Result.error(403,
                    "Admin write operation requires reliable-task.admin.audit.enabled=true");
        }
        return null;
    }

    private <T> Result<T> rejectTaskWriteIfConfirmationMissing(String operationType, String operator,
                                                               Long taskId, String traceId,
                                                               String confirmOperation) {
        if (!writeConfirmationRequired || isConfirmed(confirmOperation)) {
            return null;
        }
        String message = "X-Confirm-Operation: true is required";
        recordAdminAudit(operationType, operator, taskId, "write confirmation", "FAILED", message, traceId);
        return Result.error(400, message);
    }

    private <T> Result<T> rejectBatchWriteIfConfirmationMissing(String operationType, String operator,
                                                                String traceId, String confirmOperation) {
        if (!writeConfirmationRequired || isConfirmed(confirmOperation)) {
            return null;
        }
        String message = "X-Confirm-Operation: true is required";
        recordBatchAudit(operationType, operator, null, "write confirmation", "FAILED", message, traceId);
        return Result.error(400, message);
    }

    private boolean isConfirmed(String confirmOperation) {
        return confirmOperation != null && "true".equalsIgnoreCase(confirmOperation.trim());
    }

    private <T> Result<T> forbidIfNeeded(String action, String operator, Long taskId,
                                         boolean writeOperation, String traceId) {
        if (!authEnabled) {
            return null;
        }
        if (authorizationProvider == null) {
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
        taskOperationsStore.saveAuditLog(AuditLog.builder()
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
        taskOperationsStore.saveAuditLog(AuditLog.builder()
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
        return taskOperationsStore.createBatchOperation(operationType, operator, request.taskType(),
                request.status() == null ? null : TaskStatus.fromCode(request.status()),
                request.createTimeStart(), request.createTimeEnd(), normalizeLimit(request.limit()),
                dryRun, requestSummary(request), traceId);
    }

    private List<Long> findBatchTaskIds(BatchOperationRequest request, int limit) {
        return taskOperationsStore.findOperableTaskIds(request.taskType(),
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
        List<Long> pendingIds = taskOperationsStore.findOperableTaskIds(request.taskType(), TaskStatus.PENDING,
                request.createTimeStart(), request.createTimeEnd(), limit);
        if (pendingIds.size() >= limit) {
            return pendingIds;
        }
        List<Long> retryingIds = taskOperationsStore.findOperableTaskIds(request.taskType(), TaskStatus.RETRYING,
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

    AdminQueryGuard.NormalizedQuery normalizeOperationalQuery(LocalDateTime createTimeStart,
                                                             LocalDateTime createTimeEnd,
                                                             Integer limit,
                                                             LocalDateTime now) {
        return adminQueryGuard.normalize(createTimeStart, createTimeEnd, limit, now);
    }

    long normalizeSlowThresholdMs(Long durationMsGte) {
        return adminQueryGuard.normalizeSlowThresholdMs(durationMsGte);
    }

    int normalizeFailureTopLimit(Integer limit) {
        int maxLimit = Math.min(adminQueryGuard.getMaxLimit(), HARD_MAX_FAILURE_TOP_LIMIT);
        if (limit == null || limit <= 0) {
            return Math.min(DEFAULT_FAILURE_TOP_LIMIT, maxLimit);
        }
        return Math.min(limit, maxLimit);
    }

    String normalizeFailureTopGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "taskType,errorCode";
        }
        String normalized = groupBy.replace(" ", "");
        if ("taskType".equals(normalized)
                || "errorCode".equals(normalized)
                || "taskType,errorCode".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("groupBy must be one of: taskType, errorCode, taskType,errorCode");
    }

    AdminQueryGuard adminQueryGuard() {
        return adminQueryGuard;
    }

    private int normalizeConfiguredMax(int configuredMax, int hardMax) {
        if (configuredMax <= 0) {
            return hardMax;
        }
        return Math.min(configuredMax, hardMax);
    }

    private int normalizePayloadPreviewLength(int configuredLength) {
        if (configuredLength <= 0) {
            return DEFAULT_PAYLOAD_PREVIEW_LENGTH;
        }
        return configuredLength;
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
            TaskDetailVO detail = taskQueryStore.getTaskDetail(taskId);
            return toTaskInstance(detail);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TaskStatus statusOf(TaskInstance task) {
        return task == null ? null : task.getStatus();
    }

    private TaskInstance toTaskInstance(TaskDetailVO detail) {
        if (detail == null) {
            return null;
        }
        TaskStatus status = null;
        if (detail.getStatusCode() != null) {
            try {
                status = TaskStatus.fromCode(detail.getStatusCode());
            } catch (IllegalArgumentException ignored) {
                status = null;
            }
        }
        return TaskInstance.builder()
                .id(detail.getId())
                .taskType(detail.getTaskType())
                .bizType(detail.getBizType())
                .bizId(detail.getBizId())
                .status(status)
                .workerId(detail.getWorkerId())
                .traceId(detail.getTraceId())
                .build();
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
