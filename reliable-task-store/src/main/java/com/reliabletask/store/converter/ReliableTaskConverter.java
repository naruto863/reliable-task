package com.reliabletask.store.converter;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.store.entity.ReliableTaskAuditLogEntity;
import com.reliabletask.store.entity.ReliableTaskBatchOperationEntity;
import com.reliabletask.store.entity.ReliableTaskEntity;
import com.reliabletask.store.entity.ReliableTaskLogEntity;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskVO;
import com.reliabletask.store.entity.ReliableTaskWorkerEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entity ↔ Domain/VO 转换器
 *
 * <p>职责单一：只负责对象之间的字段映射，不掺杂任何业务逻辑。
 * 所有方法均为静态工具方法，无状态。
 */
public class ReliableTaskConverter {

    private ReliableTaskConverter() {
    }

    // ==================== TaskInstance ↔ ReliableTaskEntity ====================

    /**
     * TaskInstance → ReliableTaskEntity
     *
     * <p>领域对象转数据库实体，用于保存和更新操作。
     * 枚举类型转换为数据库存储格式（TaskStatus → Integer, RetryStrategyType → String）。
     */
    public static ReliableTaskEntity toEntity(TaskInstance task) {
        if (task == null) {
            return null;
        }

        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(task.getId());
        entity.setTaskType(task.getTaskType());
        entity.setBizType(task.getBizType());
        entity.setBizId(task.getBizId());
        entity.setBizUniqueKey(task.getBizUniqueKey());
        entity.setStatus(task.getStatus() != null ? task.getStatus().getCode() : null);
        entity.setPriority(task.getPriority());
        entity.setPayload(task.getPayload());
        entity.setExecuteCount(task.getExecuteCount());
        entity.setVersion(task.getVersion());
        entity.setMaxRetryCount(task.getMaxRetryCount());
        entity.setRetryStrategy(task.getRetryStrategy() != null ? task.getRetryStrategy().name() : null);
        entity.setRetryIntervalMs(task.getRetryIntervalMs());
        entity.setNextExecuteTime(task.getNextExecuteTime());
        entity.setShardKey(task.getShardKey());
        entity.setTenantId(task.getTenantId());
        entity.setWorkerId(task.getWorkerId());
        entity.setLockedAt(task.getLockedAt());
        entity.setLockExpireAt(task.getLockExpireAt());
        entity.setHeartbeatTime(task.getHeartbeatTime());
        entity.setLastExecuteTime(task.getLastExecuteTime());
        entity.setErrorMsg(task.getErrorMsg());
        entity.setLastErrorCode(task.getLastErrorCode());
        entity.setTraceId(task.getTraceId());
        entity.setCreateTime(task.getCreateTime());
        entity.setUpdateTime(task.getUpdateTime());
        entity.setFinishTime(task.getFinishTime());
        return entity;
    }

    /**
     * ReliableTaskEntity → TaskInstance
     *
     * <p>数据库实体转领域对象，用于业务逻辑处理。
     * 数据库格式转换回枚举类型（Integer → TaskStatus, String → RetryStrategyType）。
     */
    public static TaskInstance toDomain(ReliableTaskEntity entity) {
        if (entity == null) {
            return null;
        }

        return TaskInstance.builder()
                .id(entity.getId())
                .taskType(entity.getTaskType())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId())
                .bizUniqueKey(entity.getBizUniqueKey())
                .status(entity.getStatus() != null ? TaskStatus.fromCode(entity.getStatus()) : null)
                .priority(entity.getPriority())
                .payload(entity.getPayload())
                .executeCount(entity.getExecuteCount())
                .version(entity.getVersion())
                .maxRetryCount(entity.getMaxRetryCount())
                .retryStrategy(entity.getRetryStrategy() != null ? RetryStrategyType.valueOf(entity.getRetryStrategy()) : null)
                .retryIntervalMs(entity.getRetryIntervalMs())
                .nextExecuteTime(entity.getNextExecuteTime())
                .shardKey(entity.getShardKey())
                .tenantId(entity.getTenantId())
                .workerId(entity.getWorkerId())
                .lockedAt(entity.getLockedAt())
                .lockExpireAt(entity.getLockExpireAt())
                .heartbeatTime(entity.getHeartbeatTime())
                .lastExecuteTime(entity.getLastExecuteTime())
                .errorMsg(entity.getErrorMsg())
                .lastErrorCode(entity.getLastErrorCode())
                .traceId(entity.getTraceId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .finishTime(entity.getFinishTime())
                .build();
    }

    // ==================== Entity → VO ====================

    /**
     * ReliableTaskEntity → TaskVO
     *
     * <p>用于管理后台任务列表展示。
     */
    public static TaskVO toTaskVO(ReliableTaskEntity entity) {
        if (entity == null) {
            return null;
        }

        TaskVO vo = new TaskVO();
        vo.setId(entity.getId());
        vo.setTaskType(entity.getTaskType());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setStatusCode(entity.getStatus());
        vo.setStatusDesc(entity.getStatus() != null ? TaskStatus.fromCode(entity.getStatus()).getDescription() : null);
        vo.setPriority(entity.getPriority());
        vo.setExecuteCount(entity.getExecuteCount());
        vo.setMaxRetryCount(entity.getMaxRetryCount());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setWorkerId(entity.getWorkerId());
        vo.setNextExecuteTime(entity.getNextExecuteTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setFinishTime(entity.getFinishTime());
        return vo;
    }

    /**
     * ReliableTaskEntity → TaskDetailVO
     *
     * <p>用于管理后台任务详情展示，包含完整字段。
     */
    public static TaskDetailVO toTaskDetailVO(ReliableTaskEntity entity) {
        if (entity == null) {
            return null;
        }

        TaskDetailVO vo = new TaskDetailVO();
        vo.setId(entity.getId());
        vo.setTaskType(entity.getTaskType());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setBizUniqueKey(entity.getBizUniqueKey());
        vo.setStatusCode(entity.getStatus());
        vo.setStatusDesc(entity.getStatus() != null ? TaskStatus.fromCode(entity.getStatus()).getDescription() : null);
        vo.setPriority(entity.getPriority());
        vo.setPayload(entity.getPayload());
        vo.setExecuteCount(entity.getExecuteCount());
        vo.setMaxRetryCount(entity.getMaxRetryCount());
        vo.setRetryStrategy(entity.getRetryStrategy());
        vo.setRetryIntervalMs(entity.getRetryIntervalMs());
        vo.setNextExecuteTime(entity.getNextExecuteTime());
        vo.setShardKey(entity.getShardKey());
        vo.setTenantId(entity.getTenantId());
        vo.setWorkerId(entity.getWorkerId());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setTraceId(entity.getTraceId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setFinishTime(entity.getFinishTime());
        return vo;
    }

    /**
     * ReliableTaskLogEntity → TaskLogVO
     *
     * <p>用于管理后台执行日志展示。
     */
    public static TaskLogVO toTaskLogVO(ReliableTaskLogEntity entity) {
        if (entity == null) {
            return null;
        }

        TaskLogVO vo = new TaskLogVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setExecuteTime(entity.getExecuteTime());
        vo.setDurationMs(entity.getDurationMs());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(entity.getStatus() != null ? TaskStatus.fromCode(entity.getStatus()).getDescription() : null);
        vo.setErrorCode(entity.getErrorCode());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setWorkerId(entity.getWorkerId());
        vo.setTraceId(entity.getTraceId());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 批量转换 Entity → TaskVO
     */
    public static List<TaskVO> toTaskVOList(List<ReliableTaskEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(ReliableTaskConverter::toTaskVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换 Entity → TaskLogVO
     */
    public static List<TaskLogVO> toTaskLogVOList(List<ReliableTaskLogEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(ReliableTaskConverter::toTaskLogVO)
                .collect(Collectors.toList());
    }

    // ==================== V2 运维模型转换 ====================

    public static ReliableTaskWorkerEntity toWorkerEntity(WorkerHeartbeat heartbeat) {
        if (heartbeat == null) {
            return null;
        }
        ReliableTaskWorkerEntity entity = new ReliableTaskWorkerEntity();
        entity.setWorkerId(heartbeat.getWorkerId());
        entity.setAppName(heartbeat.getAppName());
        entity.setHostName(heartbeat.getHostName());
        entity.setIpAddress(heartbeat.getIpAddress());
        entity.setProcessId(heartbeat.getProcessId());
        entity.setStatus(toWorkerStatusCode(heartbeat.getStatus()));
        entity.setRunningTaskCount(heartbeat.getRunningTaskCount());
        entity.setMaxConcurrency(heartbeat.getMaxConcurrency());
        entity.setAvailableCapacity(heartbeat.getAvailableCapacity());
        entity.setLastHeartbeatTime(heartbeat.getLastHeartbeatTime());
        entity.setStartTime(heartbeat.getStartTime());
        return entity;
    }

    public static WorkerHeartbeat toWorkerHeartbeat(ReliableTaskWorkerEntity entity) {
        if (entity == null) {
            return null;
        }
        return WorkerHeartbeat.builder()
                .workerId(entity.getWorkerId())
                .appName(entity.getAppName())
                .hostName(entity.getHostName())
                .ipAddress(entity.getIpAddress())
                .processId(entity.getProcessId())
                .status(toWorkerStatusText(entity.getStatus()))
                .runningTaskCount(defaultInt(entity.getRunningTaskCount()))
                .maxConcurrency(defaultInt(entity.getMaxConcurrency()))
                .availableCapacity(defaultInt(entity.getAvailableCapacity()))
                .lastHeartbeatTime(entity.getLastHeartbeatTime())
                .startTime(entity.getStartTime())
                .build();
    }

    public static ReliableTaskAuditLogEntity toAuditLogEntity(AuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        ReliableTaskAuditLogEntity entity = new ReliableTaskAuditLogEntity();
        entity.setId(auditLog.getId());
        entity.setOperationType(auditLog.getOperationType());
        entity.setOperator(auditLog.getOperator());
        entity.setTargetType(auditLog.getTargetType());
        entity.setTargetId(auditLog.getTargetId());
        entity.setTaskId(auditLog.getTaskId());
        entity.setBatchOperationId(auditLog.getBatchOperationId());
        entity.setRequestSummary(auditLog.getRequestSummary());
        entity.setResult(auditLog.getResult());
        entity.setErrorMsg(auditLog.getErrorMsg());
        entity.setTraceId(auditLog.getTraceId());
        entity.setCreateTime(auditLog.getCreateTime());
        return entity;
    }

    public static AuditLog toAuditLog(ReliableTaskAuditLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return AuditLog.builder()
                .id(entity.getId())
                .operationType(entity.getOperationType())
                .operator(entity.getOperator())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .taskId(entity.getTaskId())
                .batchOperationId(entity.getBatchOperationId())
                .requestSummary(entity.getRequestSummary())
                .result(entity.getResult())
                .errorMsg(entity.getErrorMsg())
                .traceId(entity.getTraceId())
                .createTime(entity.getCreateTime())
                .build();
    }

    public static BatchOperationResult toBatchOperationResult(ReliableTaskBatchOperationEntity entity) {
        if (entity == null) {
            return null;
        }
        return BatchOperationResult.builder()
                .batchOperationId(entity.getId())
                .totalCount(defaultInt(entity.getTotalCount()))
                .successCount(defaultInt(entity.getSuccessCount()))
                .failCount(defaultInt(entity.getFailCount()))
                .failedSummary(entity.getFailedSummary())
                .dryRun(entity.getDryRun() != null && entity.getDryRun() == 1)
                .success("SUCCESS".equals(entity.getStatus()))
                .errorMsg(entity.getErrorMsg())
                .build();
    }

    public static List<WorkerHeartbeat> toWorkerHeartbeatList(List<ReliableTaskWorkerEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(ReliableTaskConverter::toWorkerHeartbeat)
                .collect(Collectors.toList());
    }

    public static List<AuditLog> toAuditLogList(List<ReliableTaskAuditLogEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(ReliableTaskConverter::toAuditLog)
                .collect(Collectors.toList());
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Integer toWorkerStatusCode(String status) {
        if ("OFFLINE".equalsIgnoreCase(status)) {
            return 0;
        }
        if ("STALE".equalsIgnoreCase(status)) {
            return 2;
        }
        return 1;
    }

    private static String toWorkerStatusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "OFFLINE";
            case 2 -> "STALE";
            default -> "ONLINE";
        };
    }
}
