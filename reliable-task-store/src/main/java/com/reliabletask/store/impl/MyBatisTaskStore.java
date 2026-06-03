package com.reliabletask.store.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.lifecycle.TaskStateMachine;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import com.reliabletask.store.converter.ReliableTaskConverter;
import com.reliabletask.store.entity.ReliableTaskAuditLogEntity;
import com.reliabletask.store.entity.ReliableTaskBatchOperationEntity;
import com.reliabletask.store.entity.ReliableTaskEntity;
import com.reliabletask.store.entity.ReliableTaskLogEntity;
import com.reliabletask.store.entity.ReliableTaskWorkerEntity;
import com.reliabletask.store.mapper.ReliableTaskAuditLogMapper;
import com.reliabletask.store.mapper.ReliableTaskBatchOperationMapper;
import com.reliabletask.store.mapper.ReliableTaskLogMapper;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import com.reliabletask.store.mapper.ReliableTaskWorkerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TaskStore 接口的 MyBatis-Plus 实现
 *
 * <p>所有状态更新方法都通过 WHERE 子句约束前置状态，
 * 确保非法状态流转不会生效（返回 false）。
 *
 * <p>并发安全策略:
 * <ul>
 *   <li>claimTask: 通过前置状态条件保证只有一个 Worker 抢占成功</li>
 *   <li>save: 先查后插 + DuplicateKeyException 兜底处理</li>
 *   <li>状态更新: 每个方法限定 WHERE 中的前置状态</li>
 * </ul>
 */
@Slf4j
@Component
public class MyBatisTaskStore implements TaskStore {

    private static final long DEFAULT_LOCK_TTL_MINUTES = 5;
    private static final int MAX_BATCH_QUERY_LIMIT = 1000;
    private static final int MAX_PAGE_SIZE = TaskQueryRequest.DEFAULT_MAX_PAGE_SIZE;

    private final ReliableTaskMapper taskMapper;
    private final ReliableTaskLogMapper taskLogMapper;
    private final ReliableTaskWorkerMapper workerMapper;
    private final ReliableTaskAuditLogMapper auditLogMapper;
    private final ReliableTaskBatchOperationMapper batchOperationMapper;

    public MyBatisTaskStore(ReliableTaskMapper taskMapper, ReliableTaskLogMapper taskLogMapper) {
        this(taskMapper, taskLogMapper, null, null, null);
    }

    public MyBatisTaskStore(ReliableTaskMapper taskMapper, ReliableTaskLogMapper taskLogMapper,
                            ReliableTaskWorkerMapper workerMapper,
                            ReliableTaskAuditLogMapper auditLogMapper,
                            ReliableTaskBatchOperationMapper batchOperationMapper) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.workerMapper = workerMapper;
        this.auditLogMapper = auditLogMapper;
        this.batchOperationMapper = batchOperationMapper;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInstance save(TaskInstance task) {
        if (task.getBizUniqueKey() != null) {
            ReliableTaskEntity existing = findLatestByBizUniqueKey(task.getBizUniqueKey());

            if (existing != null) {
                return ReliableTaskConverter.toDomain(existing);
            }
        }

        ReliableTaskEntity entity = ReliableTaskConverter.toEntity(task);

        if (entity.getStatus() == null) {
            entity.setStatus(TaskStatus.PENDING.getCode());
        }
        if (entity.getExecuteCount() == null) {
            entity.setExecuteCount(0);
        }
        if (entity.getNextExecuteTime() == null) {
            entity.setNextExecuteTime(LocalDateTime.now());
        }

        try {
            taskMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            if (task.getBizUniqueKey() == null) {
                throw e;
            }
            ReliableTaskEntity existing = findLatestByBizUniqueKey(task.getBizUniqueKey());
            if (existing != null) {
                log.info("Task already exists for bizUniqueKey={}, returning existing task id={}",
                        task.getBizUniqueKey(), existing.getId());
                return ReliableTaskConverter.toDomain(existing);
            }
            throw e;
        }
        return ReliableTaskConverter.toDomain(entity);
    }

    @Override
    public TaskInstance getById(Long id) {
        if (id == null) {
            return null;
        }
        ReliableTaskEntity entity = taskMapper.selectById(id);
        return ReliableTaskConverter.toDomain(entity);
    }

    @Override
    public TaskInstance getByBizUniqueKey(String bizUniqueKey) {
        if (bizUniqueKey == null) {
            return null;
        }
        ReliableTaskEntity entity = taskMapper.selectOne(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getBizUniqueKey, bizUniqueKey)
                        .orderByDesc(ReliableTaskEntity::getId)
                        .last("LIMIT 1")
        );
        return ReliableTaskConverter.toDomain(entity);
    }

    // ==================== 调度拉取 ====================

    @Override
    public List<TaskInstance> fetchPendingTasks(int batchSize) {
        List<ReliableTaskEntity> entities = taskMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(),
                                TaskStatus.RETRYING.getCode())
                        .le(ReliableTaskEntity::getNextExecuteTime, LocalDateTime.now())
                        .orderByAsc(ReliableTaskEntity::getPriority)
                        .orderByAsc(ReliableTaskEntity::getNextExecuteTime)
                        .last("LIMIT " + normalizeBatchLimit(batchSize))
        );
        return entities.stream()
                .map(ReliableTaskConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean claimTask(Long id, String workerId) {
        return claimTask(id, workerId, LocalDateTime.now().plusMinutes(DEFAULT_LOCK_TTL_MINUTES));
    }

    @Override
    public boolean claimTask(Long id, String workerId, LocalDateTime lockExpireAt) {
        TaskStateMachine.requireTransit(TaskStatus.PENDING, TaskStatus.RUNNING);
        TaskStateMachine.requireTransit(TaskStatus.RETRYING, TaskStatus.RUNNING);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveLockExpireAt = lockExpireAt != null
                ? lockExpireAt
                : now.plusMinutes(DEFAULT_LOCK_TTL_MINUTES);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(),
                                TaskStatus.RETRYING.getCode())
                        .le(ReliableTaskEntity::getNextExecuteTime, now)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .set(ReliableTaskEntity::getWorkerId, workerId)
                        .set(ReliableTaskEntity::getLockedAt, now)
                        .set(ReliableTaskEntity::getLockExpireAt, effectiveLockExpireAt)
                        .set(ReliableTaskEntity::getLastExecuteTime, now)
                        .setSql("execute_count = execute_count + 1")
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    @Override
    public boolean renewTaskLease(Long id, String workerId,
                                  LocalDateTime heartbeatTime,
                                  LocalDateTime lockExpireAt) {
        if (id == null || workerId == null || heartbeatTime == null || lockExpireAt == null) {
            return false;
        }
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .eq(ReliableTaskEntity::getWorkerId, workerId)
                        .set(ReliableTaskEntity::getHeartbeatTime, heartbeatTime)
                        .set(ReliableTaskEntity::getLockExpireAt, lockExpireAt)
                        .set(ReliableTaskEntity::getUpdateTime, heartbeatTime)
        );
        return rows > 0;
    }

    // ==================== 执行更新 ====================

    @Override
    public boolean markSuccess(Long id) {
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.SUCCESS);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .set(ReliableTaskEntity::getStatus, TaskStatus.SUCCESS.getCode())
                        .set(ReliableTaskEntity::getFinishTime, LocalDateTime.now())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public boolean markSuccess(TaskExecutionLease lease) {
        if (!hasExecutionLeaseIdentity(lease)) {
            return false;
        }
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.SUCCESS);
        LocalDateTime now = LocalDateTime.now();
        int rows = taskMapper.update(null,
                executionLeaseWrapper(lease)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.SUCCESS.getCode())
                        .set(ReliableTaskEntity::getFinishTime, now)
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    @Override
    public boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime) {
        return markWaitRetry(id, null, errorMsg, nextExecuteTime);
    }

    @Override
    public boolean markWaitRetry(Long id, String errorCode, String errorMsg, LocalDateTime nextExecuteTime) {
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.RETRYING);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .set(ReliableTaskEntity::getStatus, TaskStatus.RETRYING.getCode())
                        .set(ReliableTaskEntity::getErrorMsg, truncate(errorMsg, 2000))
                        .set(ReliableTaskEntity::getLastErrorCode, truncate(errorCode, 128))
                        .set(ReliableTaskEntity::getNextExecuteTime, nextExecuteTime)
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public boolean markWaitRetry(TaskExecutionLease lease, String errorMsg, LocalDateTime nextExecuteTime) {
        return markWaitRetry(lease, null, errorMsg, nextExecuteTime);
    }

    @Override
    public boolean markWaitRetry(TaskExecutionLease lease, String errorCode, String errorMsg,
                                 LocalDateTime nextExecuteTime) {
        if (!hasExecutionLeaseIdentity(lease)) {
            return false;
        }
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.RETRYING);
        LocalDateTime now = LocalDateTime.now();
        int rows = taskMapper.update(null,
                executionLeaseWrapper(lease)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.RETRYING.getCode())
                        .set(ReliableTaskEntity::getErrorMsg, truncate(errorMsg, 2000))
                        .set(ReliableTaskEntity::getLastErrorCode, truncate(errorCode, 128))
                        .set(ReliableTaskEntity::getNextExecuteTime, nextExecuteTime)
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    @Override
    public boolean markDead(Long id, String errorMsg) {
        return markDead(id, null, errorMsg);
    }

    @Override
    public boolean markDead(Long id, String errorCode, String errorMsg) {
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.DEAD);
        TaskStateMachine.requireTransit(TaskStatus.RETRYING, TaskStatus.DEAD);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.RUNNING.getCode(),
                                TaskStatus.RETRYING.getCode())
                        .set(ReliableTaskEntity::getStatus, TaskStatus.DEAD.getCode())
                        .set(ReliableTaskEntity::getErrorMsg, truncate(errorMsg, 2000))
                        .set(ReliableTaskEntity::getLastErrorCode, truncate(errorCode, 128))
                        .set(ReliableTaskEntity::getFinishTime, LocalDateTime.now())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public boolean markDead(TaskExecutionLease lease, String errorMsg) {
        return markDead(lease, null, errorMsg);
    }

    @Override
    public boolean markDead(TaskExecutionLease lease, String errorCode, String errorMsg) {
        if (!hasExecutionLeaseIdentity(lease)) {
            return false;
        }
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.DEAD);
        LocalDateTime now = LocalDateTime.now();
        int rows = taskMapper.update(null,
                executionLeaseWrapper(lease)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.DEAD.getCode())
                        .set(ReliableTaskEntity::getErrorMsg, truncate(errorMsg, 2000))
                        .set(ReliableTaskEntity::getLastErrorCode, truncate(errorCode, 128))
                        .set(ReliableTaskEntity::getFinishTime, now)
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    @Override
    public boolean cancelTask(Long id) {
        TaskStateMachine.requireTransit(TaskStatus.PENDING, TaskStatus.CANCELLED);
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.CANCELLED);
        TaskStateMachine.requireTransit(TaskStatus.RETRYING, TaskStatus.CANCELLED);
        TaskStateMachine.requireTransit(TaskStatus.FAILED, TaskStatus.CANCELLED);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(),
                                TaskStatus.RUNNING.getCode(),
                                TaskStatus.RETRYING.getCode(),
                                TaskStatus.FAILED.getCode())
                        .set(ReliableTaskEntity::getStatus, TaskStatus.CANCELLED.getCode())
                        .set(ReliableTaskEntity::getFinishTime, LocalDateTime.now())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public boolean requeueTask(Long id) {
        TaskStateMachine.requireTransit(TaskStatus.DEAD, TaskStatus.PENDING);
        TaskStateMachine.requireTransit(TaskStatus.CANCELLED, TaskStatus.PENDING);
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.DEAD.getCode(),
                                TaskStatus.CANCELLED.getCode())
                        .set(ReliableTaskEntity::getStatus, TaskStatus.PENDING.getCode())
                        .set(ReliableTaskEntity::getErrorMsg, null)
                        .set(ReliableTaskEntity::getLastErrorCode, null)
                        .set(ReliableTaskEntity::getExecuteCount, 0)
                        .set(ReliableTaskEntity::getNextExecuteTime, LocalDateTime.now())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getFinishTime, null)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public boolean updatePayload(Long id, String payload) {
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(),
                                TaskStatus.RETRYING.getCode())
                        .set(ReliableTaskEntity::getPayload, payload)
                        .set(ReliableTaskEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    // ==================== 超时恢复 ====================

    @Override
    public List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold) {
        return findTimeoutTasks(timeoutThreshold, MAX_BATCH_QUERY_LIMIT);
    }

    @Override
    public List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold, int limit) {
        List<ReliableTaskEntity> entities = taskMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .le(ReliableTaskEntity::getLockExpireAt, timeoutThreshold)
                        .orderByAsc(ReliableTaskEntity::getLockExpireAt)
                        .orderByAsc(ReliableTaskEntity::getId)
                        .last("LIMIT " + normalizeBatchLimit(limit))
        );
        return entities.stream()
                .map(ReliableTaskConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean resetTimeoutTask(Long id) {
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getId, id)
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                        .le(ReliableTaskEntity::getLockExpireAt, now)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.PENDING.getCode())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getNextExecuteTime, now)
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    @Override
    public boolean resetTimeoutTask(TaskExecutionLease lease) {
        if (!hasExecutionLeaseIdentity(lease) || lease.getLockExpireAt() == null) {
            return false;
        }
        TaskStateMachine.requireTransit(TaskStatus.RUNNING, TaskStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int rows = taskMapper.update(null,
                executionLeaseWrapper(lease)
                        .eq(ReliableTaskEntity::getLockExpireAt, lease.getLockExpireAt())
                        .le(ReliableTaskEntity::getLockExpireAt, now)
                        .set(ReliableTaskEntity::getStatus, TaskStatus.PENDING.getCode())
                        .set(ReliableTaskEntity::getWorkerId, null)
                        .set(ReliableTaskEntity::getLockedAt, null)
                        .set(ReliableTaskEntity::getLockExpireAt, null)
                        .set(ReliableTaskEntity::getHeartbeatTime, null)
                        .set(ReliableTaskEntity::getNextExecuteTime, now)
                        .setSql("version = version + 1")
                        .set(ReliableTaskEntity::getUpdateTime, now)
        );
        return rows > 0;
    }

    // ==================== 日志记录 ====================

    @Override
    public void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                        boolean success, long durationMs, String errorCode, String errorMessage,
                        String workerId, String traceId) {
        ReliableTaskLogEntity logEntity = new ReliableTaskLogEntity();
        logEntity.setTaskId(taskId);
        logEntity.setAttemptNo(executeNo);
        logEntity.setStatusBefore(statusBefore);
        logEntity.setStatusAfter(statusAfter);
        logEntity.setExecuteTime(LocalDateTime.now());
        logEntity.setDurationMs(durationMs);
        logEntity.setStatus(success ? TaskStatus.SUCCESS.getCode() : TaskStatus.FAILED.getCode());
        logEntity.setErrorCode(truncate(errorCode, 128));
        logEntity.setErrorMsg(truncate(errorMessage, 4000));
        logEntity.setWorkerId(workerId);
        logEntity.setTraceId(traceId);
        taskLogMapper.insert(logEntity);
    }

    // ==================== 查询操作 ====================

    @Override
    public PageResult<TaskVO> listTasks(TaskQueryRequest request) {
        TaskQueryRequest effectiveRequest = request != null ? request : new TaskQueryRequest();
        int pageNum = effectiveRequest.normalizedPageNum();
        int pageSize = effectiveRequest.normalizedPageSize(MAX_PAGE_SIZE);
        Page<ReliableTaskEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReliableTaskEntity> wrapper = buildQueryWrapper(effectiveRequest);
        wrapper.orderByDesc(ReliableTaskEntity::getId);

        Page<ReliableTaskEntity> result = taskMapper.selectPage(page, wrapper);
        List<TaskVO> voList = ReliableTaskConverter.toTaskVOList(result.getRecords());
        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public TaskDetailVO getTaskDetail(Long id) {
        if (id == null) {
            return null;
        }
        ReliableTaskEntity entity = taskMapper.selectById(id);
        return ReliableTaskConverter.toTaskDetailVO(entity);
    }

    @Override
    public List<TaskLogVO> getTaskLogs(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<ReliableTaskLogEntity> entities = taskLogMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskLogEntity>()
                        .eq(ReliableTaskLogEntity::getTaskId, taskId)
                        .orderByDesc(ReliableTaskLogEntity::getId)
        );
        return ReliableTaskConverter.toTaskLogVOList(entities);
    }

    @Override
    public TaskStatsVO getStats() {
        TaskStatsVO stats = new TaskStatsVO();

        List<Map<String, Object>> statusRows = taskMapper.countByStatus();

        Map<Integer, Long> statusCount = parseStatusCount(statusRows);

        stats.setStatusCount(statusCount);
        stats.setTotalTasks(statusCount.values().stream().mapToLong(Long::longValue).sum());
        stats.setPendingTasks(statusCount.getOrDefault(TaskStatus.PENDING.getCode(), 0L)
                + statusCount.getOrDefault(TaskStatus.RETRYING.getCode(), 0L));
        stats.setDeadTasks(statusCount.getOrDefault(TaskStatus.DEAD.getCode(), 0L));

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        Long todayNew = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .ge(ReliableTaskEntity::getCreateTime, todayStart)
        );
        stats.setTodayNewTasks(todayNew);

        Long todaySuccess = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.SUCCESS.getCode())
                        .ge(ReliableTaskEntity::getFinishTime, todayStart)
        );
        stats.setTodaySuccessTasks(todaySuccess);

        Long todayFailed = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.DEAD.getCode())
                        .ge(ReliableTaskEntity::getFinishTime, todayStart)
        );
        stats.setTodayFailedTasks(todayFailed);

        stats.setOldestPendingAgeSeconds(resolveOldestPendingAgeSeconds());
        stats.setTaskTypeStats(parseTaskTypeCount(taskMapper.countByTaskType()));

        return stats;
    }

    private long resolveOldestPendingAgeSeconds() {
        ReliableTaskEntity oldest = taskMapper.selectOne(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(), TaskStatus.RETRYING.getCode())
                        .orderByAsc(ReliableTaskEntity::getCreateTime)
                        .last("LIMIT 1")
        );
        if (oldest == null || oldest.getCreateTime() == null) {
            return 0L;
        }
        return Math.max(Duration.between(oldest.getCreateTime(), LocalDateTime.now()).getSeconds(), 0L);
    }

    // ==================== V2 运维能力 ====================

    @Override
    public void reportWorkerHeartbeat(WorkerHeartbeat heartbeat) {
        if (heartbeat == null || heartbeat.getWorkerId() == null || workerMapper == null) {
            return;
        }
        try {
            ReliableTaskWorkerEntity entity = ReliableTaskConverter.toWorkerEntity(heartbeat);
            LocalDateTime now = LocalDateTime.now();
            entity.setUpdateTime(now);

            ReliableTaskWorkerEntity existing = workerMapper.selectById(entity.getWorkerId());
            if (existing == null) {
                entity.setCreateTime(now);
                workerMapper.insert(entity);
            } else {
                workerMapper.updateById(entity);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to report worker heartbeat: workerId={}, reason={}",
                    heartbeat.getWorkerId(), e.getMessage());
        }
    }

    @Override
    public List<WorkerHeartbeat> listWorkers() {
        if (workerMapper == null) {
            return List.of();
        }
        List<ReliableTaskWorkerEntity> entities = workerMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskWorkerEntity>()
                        .orderByDesc(ReliableTaskWorkerEntity::getLastHeartbeatTime)
        );
        return ReliableTaskConverter.toWorkerHeartbeatList(entities);
    }

    @Override
    public List<WorkerHeartbeat> findStaleWorkers(LocalDateTime heartbeatBefore) {
        if (workerMapper == null || heartbeatBefore == null) {
            return List.of();
        }
        List<ReliableTaskWorkerEntity> entities = workerMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskWorkerEntity>()
                        .le(ReliableTaskWorkerEntity::getLastHeartbeatTime, heartbeatBefore)
                        .orderByAsc(ReliableTaskWorkerEntity::getLastHeartbeatTime)
        );
        return ReliableTaskConverter.toWorkerHeartbeatList(entities);
    }

    @Override
    public void saveAuditLog(AuditLog auditLog) {
        if (auditLog == null || auditLogMapper == null) {
            return;
        }
        try {
            ReliableTaskAuditLogEntity entity = ReliableTaskConverter.toAuditLogEntity(auditLog);
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            auditLogMapper.insert(entity);
        } catch (RuntimeException e) {
            log.warn("Failed to save audit log: operationType={}, targetType={}, targetId={}, reason={}",
                    auditLog.getOperationType(), auditLog.getTargetType(), auditLog.getTargetId(), e.getMessage());
        }
    }

    @Override
    public List<AuditLog> getAuditLogsByTaskId(Long taskId) {
        if (auditLogMapper == null || taskId == null) {
            return List.of();
        }
        List<ReliableTaskAuditLogEntity> entities = auditLogMapper.selectList(
                new LambdaQueryWrapper<ReliableTaskAuditLogEntity>()
                        .eq(ReliableTaskAuditLogEntity::getTaskId, taskId)
                        .orderByDesc(ReliableTaskAuditLogEntity::getCreateTime)
        );
        return ReliableTaskConverter.toAuditLogList(entities);
    }

    @Override
    public PageResult<AuditLog> listAuditLogs(String operator,
                                              LocalDateTime createTimeStart,
                                              LocalDateTime createTimeEnd,
                                              int pageNum,
                                              int pageSize) {
        if (auditLogMapper == null) {
            return PageResult.of(List.of(), 0, normalizePageNum(pageNum), normalizePageSize(pageSize));
        }
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        Page<ReliableTaskAuditLogEntity> page = new Page<>(normalizedPageNum, normalizedPageSize);
        LambdaQueryWrapper<ReliableTaskAuditLogEntity> wrapper =
                new LambdaQueryWrapper<ReliableTaskAuditLogEntity>()
                        .orderByDesc(ReliableTaskAuditLogEntity::getCreateTime);
        if (operator != null && !operator.isBlank()) {
            wrapper.eq(ReliableTaskAuditLogEntity::getOperator, operator);
        }
        if (createTimeStart != null) {
            wrapper.ge(ReliableTaskAuditLogEntity::getCreateTime, createTimeStart);
        }
        if (createTimeEnd != null) {
            wrapper.le(ReliableTaskAuditLogEntity::getCreateTime, createTimeEnd);
        }
        Page<ReliableTaskAuditLogEntity> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(ReliableTaskConverter.toAuditLogList(result.getRecords()),
                result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public Long createBatchOperation(String operationType, String operator, String taskType,
                                     TaskStatus taskStatus, LocalDateTime createTimeStart,
                                     LocalDateTime createTimeEnd, int limit, boolean dryRun,
                                     String requestCondition, String traceId) {
        requireBatchOperationMapper();

        LocalDateTime now = LocalDateTime.now();
        ReliableTaskBatchOperationEntity entity = new ReliableTaskBatchOperationEntity();
        entity.setOperationType(operationType);
        entity.setStatus("PENDING");
        entity.setOperator(operator);
        entity.setTaskType(taskType);
        entity.setTaskStatus(taskStatus == null ? null : taskStatus.getCode());
        entity.setCreateTimeStart(createTimeStart);
        entity.setCreateTimeEnd(createTimeEnd);
        entity.setOperationLimit(normalizeBatchLimit(limit));
        entity.setDryRun(dryRun ? 1 : 0);
        entity.setRequestCondition(requestCondition);
        entity.setTraceId(traceId);
        entity.setTotalCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        batchOperationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateBatchOperationResult(BatchOperationResult result) {
        requireBatchOperationMapper();
        if (result == null || result.getBatchOperationId() == null) {
            return false;
        }

        String status = result.isSuccess()
                ? "SUCCESS"
                : result.getSuccessCount() > 0 ? "PARTIAL_FAILED" : "FAILED";

        int rows = batchOperationMapper.update(null,
                new LambdaUpdateWrapper<ReliableTaskBatchOperationEntity>()
                        .eq(ReliableTaskBatchOperationEntity::getId, result.getBatchOperationId())
                        .set(ReliableTaskBatchOperationEntity::getStatus, status)
                        .set(ReliableTaskBatchOperationEntity::getTotalCount, result.getTotalCount())
                        .set(ReliableTaskBatchOperationEntity::getSuccessCount, result.getSuccessCount())
                        .set(ReliableTaskBatchOperationEntity::getFailCount, result.getFailCount())
                        .set(ReliableTaskBatchOperationEntity::getFailedSummary, result.getFailedSummary())
                        .set(ReliableTaskBatchOperationEntity::getErrorMsg, result.getErrorMsg())
                        .set(ReliableTaskBatchOperationEntity::getFinishTime, LocalDateTime.now())
                        .set(ReliableTaskBatchOperationEntity::getUpdateTime, LocalDateTime.now())
        );
        return rows > 0;
    }

    @Override
    public BatchOperationResult getBatchOperationResult(Long batchOperationId) {
        requireBatchOperationMapper();
        if (batchOperationId == null) {
            return null;
        }
        return ReliableTaskConverter.toBatchOperationResult(batchOperationMapper.selectById(batchOperationId));
    }

    @Override
    public List<Long> findOperableTaskIds(String taskType, TaskStatus status,
                                          LocalDateTime createTimeStart,
                                          LocalDateTime createTimeEnd,
                                          int limit) {
        LambdaQueryWrapper<ReliableTaskEntity> wrapper = new LambdaQueryWrapper<ReliableTaskEntity>()
                .select(ReliableTaskEntity::getId)
                .orderByAsc(ReliableTaskEntity::getId)
                .last("LIMIT " + normalizeBatchLimit(limit));

        if (taskType != null) {
            wrapper.eq(ReliableTaskEntity::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(ReliableTaskEntity::getStatus, status.getCode());
        }
        if (createTimeStart != null) {
            wrapper.ge(ReliableTaskEntity::getCreateTime, createTimeStart);
        }
        if (createTimeEnd != null) {
            wrapper.le(ReliableTaskEntity::getCreateTime, createTimeEnd);
        }

        return taskMapper.selectList(wrapper).stream()
                .map(ReliableTaskEntity::getId)
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    private void requireBatchOperationMapper() {
        if (batchOperationMapper == null) {
            throw new UnsupportedOperationException("Batch operation mapper is not configured");
        }
    }

    private boolean hasExecutionLeaseIdentity(TaskExecutionLease lease) {
        return lease != null
                && lease.getTaskId() != null
                && lease.getWorkerId() != null
                && !lease.getWorkerId().isBlank()
                && lease.getVersion() != null;
    }

    private LambdaUpdateWrapper<ReliableTaskEntity> executionLeaseWrapper(TaskExecutionLease lease) {
        return new LambdaUpdateWrapper<ReliableTaskEntity>()
                .eq(ReliableTaskEntity::getId, lease.getTaskId())
                .eq(ReliableTaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                .eq(ReliableTaskEntity::getWorkerId, lease.getWorkerId())
                .eq(ReliableTaskEntity::getVersion, lease.getVersion())
                .eq(lease.getLockedAt() != null, ReliableTaskEntity::getLockedAt, lease.getLockedAt());
    }

    private int normalizeBatchLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, MAX_BATCH_QUERY_LIMIT);
    }

    private int normalizePageNum(int pageNum) {
        return pageNum <= 0 ? TaskQueryRequest.DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return TaskQueryRequest.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private Map<Integer, Long> parseStatusCount(List<Map<String, Object>> statusRows) {
        Map<Integer, Long> statusCount = new HashMap<>();
        for (Map<String, Object> row : statusRows) {
            Object statusObj = row.get("status");
            if (statusObj != null) {
                Integer code = ((Number) statusObj).intValue();
                statusCount.put(code, extractCount(row));
            }
        }
        return statusCount;
    }

    private Map<String, Long> parseTaskTypeCount(List<Map<String, Object>> typeRows) {
        Map<String, Long> taskTypeStats = new HashMap<>();
        for (Map<String, Object> row : typeRows) {
            Object typeObj = row.get("taskType");
            if (typeObj != null) {
                taskTypeStats.put(typeObj.toString(), extractCount(row));
            }
        }
        return taskTypeStats;
    }

    private long extractCount(Map<String, Object> row) {
        Object countObj = row.get("count");
        if (countObj == null) {
            countObj = row.get("COUNT(*)");
        }
        if (countObj == null) {
            return 0L;
        }
        return ((Number) countObj).longValue();
    }

    private ReliableTaskEntity findLatestByBizUniqueKey(String bizUniqueKey) {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getBizUniqueKey, bizUniqueKey)
                        .orderByDesc(ReliableTaskEntity::getId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 构建多条件查询 Wrapper
     */
    private LambdaQueryWrapper<ReliableTaskEntity> buildQueryWrapper(TaskQueryRequest request) {
        LambdaQueryWrapper<ReliableTaskEntity> wrapper = new LambdaQueryWrapper<>();

        if (request.getStatus() != null) {
            wrapper.eq(ReliableTaskEntity::getStatus, request.getStatus().getCode());
        }
        if (request.getTaskType() != null) {
            wrapper.eq(ReliableTaskEntity::getTaskType, request.getTaskType());
        }
        if (request.getBizType() != null) {
            wrapper.eq(ReliableTaskEntity::getBizType, request.getBizType());
        }
        if (request.getBizId() != null) {
            wrapper.like(ReliableTaskEntity::getBizId, request.getBizId());
        }
        if (request.getWorkerId() != null) {
            wrapper.eq(ReliableTaskEntity::getWorkerId, request.getWorkerId());
        }
        if (request.getTraceId() != null) {
            wrapper.eq(ReliableTaskEntity::getTraceId, request.getTraceId());
        }
        if (request.getTenantId() != null) {
            wrapper.eq(ReliableTaskEntity::getTenantId, request.getTenantId());
        }
        if (request.getCreateTimeStart() != null) {
            wrapper.ge(ReliableTaskEntity::getCreateTime, request.getCreateTimeStart());
        }
        if (request.getCreateTimeEnd() != null) {
            wrapper.le(ReliableTaskEntity::getCreateTime, request.getCreateTimeEnd());
        }

        return wrapper;
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
