package com.reliabletask.store.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MyBatisTaskStore 测试")
@ExtendWith(MockitoExtension.class)
class MyBatisTaskStoreTest {

    @Mock
    private ReliableTaskMapper taskMapper;

    @Mock
    private ReliableTaskLogMapper taskLogMapper;

    @Mock
    private ReliableTaskWorkerMapper workerMapper;

    @Mock
    private ReliableTaskAuditLogMapper auditLogMapper;

    @Mock
    private ReliableTaskBatchOperationMapper batchOperationMapper;

    private MyBatisTaskStore taskStore;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration, ReliableTaskEntity.class.getPackageName());

        TableInfoHelper.initTableInfo(assistant, ReliableTaskEntity.class);
        TableInfoHelper.initTableInfo(assistant, ReliableTaskLogEntity.class);
        TableInfoHelper.initTableInfo(assistant, ReliableTaskWorkerEntity.class);
        TableInfoHelper.initTableInfo(assistant, ReliableTaskAuditLogEntity.class);
        TableInfoHelper.initTableInfo(assistant, ReliableTaskBatchOperationEntity.class);
    }

    @BeforeEach
    void setUp() {
        taskStore = new MyBatisTaskStore(taskMapper, taskLogMapper,
                workerMapper, auditLogMapper, batchOperationMapper);
    }

    // ==================== save ====================

    @Test
    @DisplayName("save - 新任务保存成功")
    void save_newTask_insertsSuccessfully() {
        TaskInstance task = TaskInstance.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-001")
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001")
                .status(TaskStatus.PENDING)
                .priority(5)
                .payload("{\"orderId\":\"ORD-001\"}")
                .executeCount(0)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.EXPONENTIAL)
                .retryIntervalMs(1000L)
                .nextExecuteTime(LocalDateTime.now())
                .build();

        when(taskMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ReliableTaskEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(taskMapper).insert(any());

        TaskInstance result = taskStore.save(task);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(taskMapper).insert(any());
    }

    @Test
    @DisplayName("save - 非终态已存在返回已有记录")
    void save_existingNonTerminal_returnsExisting() {
        TaskInstance task = TaskInstance.builder()
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001")
                .build();

        ReliableTaskEntity existing = new ReliableTaskEntity();
        existing.setId(10L);
        existing.setStatus(TaskStatus.PENDING.getCode());

        when(taskMapper.selectOne(any())).thenReturn(existing);

        TaskInstance result = taskStore.save(task);

        assertEquals(10L, result.getId());
        assertEquals(TaskStatus.PENDING, result.getStatus());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("save - 终态已存在仍返回已有记录")
    void save_existingTerminal_returnsExisting() {
        TaskInstance task = TaskInstance.builder()
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001")
                .build();

        ReliableTaskEntity existing = new ReliableTaskEntity();
        existing.setId(10L);
        existing.setStatus(TaskStatus.SUCCESS.getCode());

        when(taskMapper.selectOne(any())).thenReturn(existing);

        TaskInstance result = taskStore.save(task);

        assertEquals(10L, result.getId());
        assertEquals(TaskStatus.SUCCESS, result.getStatus());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("save - 唯一键冲突时兜底返回已有记录")
    void save_duplicateKey_returnsExisting() {
        TaskInstance task = TaskInstance.builder()
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001")
                .status(TaskStatus.PENDING)
                .build();

        ReliableTaskEntity existing = new ReliableTaskEntity();
        existing.setId(20L);
        existing.setStatus(TaskStatus.PENDING.getCode());

        when(taskMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(existing);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(taskMapper).insert(any());

        TaskInstance result = taskStore.save(task);

        assertEquals(20L, result.getId());
        assertEquals(TaskStatus.PENDING, result.getStatus());
        verify(taskMapper).insert(any());
    }

    @Test
    @DisplayName("save - 默认值设置")
    void save_setsDefaultValues() {
        TaskInstance task = TaskInstance.builder()
                .bizUniqueKey("TYPE:BIZ:ID")
                .build();

        when(taskMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ReliableTaskEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(taskMapper).insert(any());

        taskStore.save(task);

        ArgumentCaptor<ReliableTaskEntity> captor = ArgumentCaptor.forClass(ReliableTaskEntity.class);
        verify(taskMapper).insert(captor.capture());
        ReliableTaskEntity captured = captor.getValue();

        assertEquals(TaskStatus.PENDING.getCode(), captured.getStatus());
        assertEquals(0, captured.getExecuteCount());
        assertNotNull(captured.getNextExecuteTime());
    }

    // ==================== getById / getByBizUniqueKey ====================

    @Test
    @DisplayName("getById - 正常查询")
    void getById_validId_returnsTaskInstance() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setStatus(TaskStatus.PENDING.getCode());
        when(taskMapper.selectById(1L)).thenReturn(entity);

        TaskInstance result = taskStore.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getById - null 返回 null")
    void getById_nullId_returnsNull() {
        assertNull(taskStore.getById(null));
    }

    @Test
    @DisplayName("getByBizUniqueKey - 正常查询")
    void getByBizUniqueKey_validKey_returnsTaskInstance() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setBizUniqueKey("TYPE:BIZ:ID");
        entity.setStatus(TaskStatus.RUNNING.getCode());
        when(taskMapper.selectOne(any())).thenReturn(entity);

        TaskInstance result = taskStore.getByBizUniqueKey("TYPE:BIZ:ID");

        assertNotNull(result);
        assertEquals(TaskStatus.RUNNING, result.getStatus());
    }

    // ==================== fetchPendingTasks ====================

    @Test
    @DisplayName("fetchPendingTasks - 返回 PENDING 和 RETRYING 任务")
    void fetchPendingTasks_returnsPendingAndRetryingTasks() {
        ReliableTaskEntity e1 = new ReliableTaskEntity();
        e1.setId(1L);
        e1.setStatus(TaskStatus.PENDING.getCode());
        e1.setPriority(3);

        ReliableTaskEntity e2 = new ReliableTaskEntity();
        e2.setId(2L);
        e2.setStatus(TaskStatus.RETRYING.getCode());
        e2.setPriority(5);

        when(taskMapper.selectList(any())).thenReturn(List.of(e1, e2));

        List<TaskInstance> result = taskStore.fetchPendingTasks(10);

        assertEquals(2, result.size());
        assertEquals(TaskStatus.PENDING, result.get(0).getStatus());
        assertEquals(TaskStatus.RETRYING, result.get(1).getStatus());
    }

    // ==================== claimTask ====================

    @Test
    @DisplayName("claimTask - 抢占成功")
    void claimTask_success_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        boolean result = taskStore.claimTask(1L, "worker-1");

        assertTrue(result);
        verify(taskMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("claimTask - 抢占失败返回 false")
    void claimTask_failure_returnsFalse() {
        when(taskMapper.update(isNull(), any())).thenReturn(0);

        boolean result = taskStore.claimTask(1L, "worker-1");

        assertFalse(result);
    }

    @Test
    @DisplayName("claimTask - 支持显式锁过期时间")
    void claimTask_withExplicitLockExpireAt_returnsTrue() {
        LocalDateTime lockExpireAt = LocalDateTime.now().plusSeconds(45);
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        boolean result = taskStore.claimTask(1L, "worker-1", lockExpireAt);

        assertTrue(result);
        verify(taskMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("renewTaskLease - 续约运行中任务")
    void renewTaskLease_updatesHeartbeatAndLockExpireAt() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        boolean result = taskStore.renewTaskLease(1L, "worker-1",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));

        assertTrue(result);
        verify(taskMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    // ==================== markSuccess ====================

    @Test
    @DisplayName("markSuccess - 标记成功")
    void markSuccess_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.markSuccess(1L));
    }

    // ==================== markWaitRetry ====================

    @Test
    @DisplayName("markWaitRetry - 标记等待重试")
    void markWaitRetry_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.markWaitRetry(1L, "timeout", LocalDateTime.now().plusSeconds(10)));
    }

    // ==================== markDead ====================

    @Test
    @DisplayName("markDead - 标记死信")
    void markDead_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.markDead(1L, "max retries"));
    }

    // ==================== cancelTask ====================

    @Test
    @DisplayName("cancelTask - 取消成功")
    void cancelTask_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.cancelTask(1L));
    }

    // ==================== requeueTask ====================

    @Test
    @DisplayName("requeueTask - 重新入队")
    void requeueTask_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.requeueTask(1L));
    }

    // ==================== updatePayload ====================

    @Test
    @DisplayName("updatePayload - 更新参数")
    void updatePayload_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.updatePayload(1L, "{\"new\":\"data\"}"));
    }

    // ==================== findTimeoutTasks ====================

    @Test
    @DisplayName("findTimeoutTasks - 查找超时任务")
    void findTimeoutTasks_returnsTimeoutTasks() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setStatus(TaskStatus.RUNNING.getCode());
        when(taskMapper.selectList(any())).thenReturn(List.of(entity));

        List<TaskInstance> result = taskStore.findTimeoutTasks(LocalDateTime.now().minusMinutes(5));

        assertEquals(1, result.size());
        assertEquals(TaskStatus.RUNNING, result.get(0).getStatus());
    }

    // ==================== resetTimeoutTask ====================

    @Test
    @DisplayName("resetTimeoutTask - 重置超时任务")
    void resetTimeoutTask_returnsTrue() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(taskStore.resetTimeoutTask(1L));
    }

    // ==================== saveLog ====================

    @Test
    @DisplayName("saveLog - 保存执行日志")
    void saveLog_insertsLog() {
        taskStore.saveLog(1L, 1, "RUNNING", "RETRYING", false, 1500L,
                "RetryableException", "temporary failure", "worker-1", "trace-1");

        ArgumentCaptor<ReliableTaskLogEntity> captor = ArgumentCaptor.forClass(ReliableTaskLogEntity.class);
        verify(taskLogMapper).insert(captor.capture());
        ReliableTaskLogEntity log = captor.getValue();
        assertEquals(1, log.getAttemptNo());
        assertEquals("RUNNING", log.getStatusBefore());
        assertEquals("RETRYING", log.getStatusAfter());
        assertEquals("RetryableException", log.getErrorCode());
        assertEquals("temporary failure", log.getErrorMsg());
        assertEquals("worker-1", log.getWorkerId());
        assertEquals("trace-1", log.getTraceId());
    }

    // ==================== listTasks ====================

    @Test
    @DisplayName("listTasks - 分页查询")
    void listTasks_returnsPageResult() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setTaskType("TYPE_A");
        entity.setStatus(TaskStatus.PENDING.getCode());

        Page<ReliableTaskEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1L);

        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .pageNum(1)
                .pageSize(10)
                .build();

        PageResult<TaskVO> result = taskStore.listTasks(request);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("TYPE_A", result.getRecords().get(0).getTaskType());
    }

    @Test
    @DisplayName("listTasks - 多条件筛选")
    void listTasks_withFilters() {
        Page<ReliableTaskEntity> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .status(TaskStatus.PENDING)
                .taskType("TYPE_A")
                .bizType("ORDER")
                .bizId("ORD")
                .workerId("worker-1")
                .traceId("trace-1")
                .tenantId("tenant-1")
                .createTimeStart(LocalDateTime.now().minusDays(1))
                .createTimeEnd(LocalDateTime.now())
                .pageNum(1)
                .pageSize(10)
                .build();

        PageResult<TaskVO> result = taskStore.listTasks(request);

        assertNotNull(result);
        verify(taskMapper).selectPage(any(), any());
    }

    // ==================== getTaskDetail ====================

    @Test
    @DisplayName("getTaskDetail - 查询详情")
    void getTaskDetail_returnsDetail() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setTaskType("TYPE_A");
        entity.setStatus(TaskStatus.PENDING.getCode());
        when(taskMapper.selectById(1L)).thenReturn(entity);

        TaskDetailVO result = taskStore.getTaskDetail(1L);

        assertNotNull(result);
        assertEquals("TYPE_A", result.getTaskType());
    }

    // ==================== getTaskLogs ====================

    @Test
    @DisplayName("getTaskLogs - 查询日志")
    void getTaskLogs_returnsLogs() {
        ReliableTaskLogEntity logEntity = new ReliableTaskLogEntity();
        logEntity.setId(100L);
        logEntity.setTaskId(1L);
        logEntity.setAttemptNo(2);
        logEntity.setStatusBefore("RUNNING");
        logEntity.setStatusAfter("SUCCESS");
        logEntity.setStatus(TaskStatus.SUCCESS.getCode());
        when(taskLogMapper.selectList(any())).thenReturn(List.of(logEntity));

        List<TaskLogVO> result = taskStore.getTaskLogs(1L);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getAttemptNo());
        assertEquals("RUNNING", result.get(0).getStatusBefore());
        assertEquals("SUCCESS", result.get(0).getStatusAfter());
        assertEquals("成功", result.get(0).getStatusDesc());
    }

    // ==================== getStats ====================

    @Test
    @DisplayName("getStats - 获取统计")
    void getStats_returnsStats() {
        when(taskMapper.countByStatus()).thenReturn(List.of(
                Map.of("status", 0, "count", 2L),
                Map.of("status", 1, "count", 1L),
                Map.of("status", 2, "count", 1L),
                Map.of("status", 5, "count", 1L)
        ));
        when(taskMapper.countByTaskType()).thenReturn(List.of(
                Map.of("taskType", "TYPE_A", "count", 3L),
                Map.of("taskType", "TYPE_B", "count", 2L)
        ));
        when(taskMapper.selectCount(any())).thenReturn(2L, 1L, 1L);

        TaskStatsVO result = taskStore.getStats();

        assertNotNull(result);
        assertEquals(5, result.getTotalTasks());
        assertTrue(result.getPendingTasks() > 0);
        assertTrue(result.getDeadTasks() > 0);
        assertEquals(3L, result.getTaskTypeStats().get("TYPE_A"));
    }

    // ==================== V2 运维能力 ====================

    @Test
    @DisplayName("reportWorkerHeartbeat - 新 Worker 插入")
    void reportWorkerHeartbeat_newWorker_inserts() {
        when(workerMapper.selectById("worker-1")).thenReturn(null);

        taskStore.reportWorkerHeartbeat(WorkerHeartbeat.builder()
                .workerId("worker-1")
                .status("ONLINE")
                .runningTaskCount(2)
                .maxConcurrency(8)
                .availableCapacity(6)
                .lastHeartbeatTime(LocalDateTime.now())
                .build());

        ArgumentCaptor<ReliableTaskWorkerEntity> captor =
                ArgumentCaptor.forClass(ReliableTaskWorkerEntity.class);
        verify(workerMapper).insert(captor.capture());
        assertEquals("worker-1", captor.getValue().getWorkerId());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("findStaleWorkers - 按心跳时间查询")
    void findStaleWorkers_returnsWorkers() {
        ReliableTaskWorkerEntity entity = new ReliableTaskWorkerEntity();
        entity.setWorkerId("worker-1");
        entity.setStatus(2);
        entity.setLastHeartbeatTime(LocalDateTime.now().minusMinutes(10));
        when(workerMapper.selectList(any())).thenReturn(List.of(entity));

        List<WorkerHeartbeat> result = taskStore.findStaleWorkers(LocalDateTime.now().minusMinutes(5));

        assertEquals(1, result.size());
        assertEquals("STALE", result.get(0).getStatus());
    }

    @Test
    @DisplayName("saveAuditLog - 审计写入失败不向外抛出")
    void saveAuditLog_insertFailure_doesNotThrow() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any());

        assertDoesNotThrow(() -> taskStore.saveAuditLog(AuditLog.builder()
                .operationType("TASK_RETRY")
                .targetType("TASK")
                .targetId("1")
                .result("FAILED")
                .build()));
    }

    @Test
    @DisplayName("getAuditLogsByTaskId - 按任务 ID 查询审计")
    void getAuditLogsByTaskId_returnsLogs() {
        ReliableTaskAuditLogEntity entity = new ReliableTaskAuditLogEntity();
        entity.setId(1L);
        entity.setTaskId(10L);
        entity.setOperationType("TASK_CANCEL");
        when(auditLogMapper.selectList(any())).thenReturn(List.of(entity));

        List<AuditLog> result = taskStore.getAuditLogsByTaskId(10L);

        assertEquals(1, result.size());
        assertEquals("TASK_CANCEL", result.get(0).getOperationType());
        verify(auditLogMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listAuditLogs - 按操作人和时间分页查询")
    void listAuditLogs_returnsPageResult() {
        ReliableTaskAuditLogEntity entity = new ReliableTaskAuditLogEntity();
        entity.setId(1L);
        entity.setOperator("admin");
        entity.setOperationType("TASK_RETRY");
        Page<ReliableTaskAuditLogEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<AuditLog> result = taskStore.listAuditLogs("admin",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("TASK_RETRY", result.getRecords().get(0).getOperationType());
        verify(auditLogMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("createBatchOperation - 创建批量操作并限制最大 limit")
    void createBatchOperation_insertsWithSafeLimit() {
        doAnswer(invocation -> {
            ReliableTaskBatchOperationEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(batchOperationMapper).insert(any());

        Long id = taskStore.createBatchOperation("REQUEUE_DEAD", "admin", "TYPE_A",
                TaskStatus.DEAD, null, null, 5000, false, "{\"status\":\"DEAD\"}", "trace-1");

        assertEquals(100L, id);
        ArgumentCaptor<ReliableTaskBatchOperationEntity> captor =
                ArgumentCaptor.forClass(ReliableTaskBatchOperationEntity.class);
        verify(batchOperationMapper).insert(captor.capture());
        assertEquals(1000, captor.getValue().getOperationLimit());
        assertEquals(TaskStatus.DEAD.getCode(), captor.getValue().getTaskStatus());
    }

    @Test
    @DisplayName("updateBatchOperationResult - 更新结果")
    void updateBatchOperationResult_updatesRecord() {
        when(batchOperationMapper.update(isNull(), any())).thenReturn(1);

        boolean result = taskStore.updateBatchOperationResult(BatchOperationResult.builder()
                .batchOperationId(100L)
                .totalCount(3)
                .successCount(2)
                .failCount(1)
                .failedSummary("3")
                .success(false)
                .build());

        assertTrue(result);
        verify(batchOperationMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("findOperableTaskIds - 按条件和 limit 查询")
    void findOperableTaskIds_usesLimit() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        when(taskMapper.selectList(any())).thenReturn(List.of(entity));

        List<Long> ids = taskStore.findOperableTaskIds("TYPE_A", TaskStatus.DEAD,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 10);

        assertEquals(List.of(1L), ids);
        verify(taskMapper).selectList(any(LambdaQueryWrapper.class));
    }
}
