package com.reliabletask.store.converter;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskVO;
import com.reliabletask.store.entity.ReliableTaskEntity;
import com.reliabletask.store.entity.ReliableTaskLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReliableTaskConverter 测试")
class ReliableTaskConverterTest {

    @Test
    @DisplayName("toEntity - TaskInstance 转 Entity")
    void toEntity_convertsAllFields() {
        TaskInstance task = TaskInstance.builder()
                .id(1L)
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-001")
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001")
                .status(TaskStatus.PENDING)
                .priority(3)
                .payload("{\"orderId\":\"ORD-001\"}")
                .executeCount(0)
                .version(7)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.EXPONENTIAL)
                .retryIntervalMs(1000L)
                .nextExecuteTime(LocalDateTime.of(2026, 4, 4, 12, 0))
                .shardKey("shard1")
                .tenantId("tenant1")
                .workerId("worker-1")
                .lockedAt(LocalDateTime.of(2026, 4, 4, 11, 0))
                .lockExpireAt(LocalDateTime.of(2026, 4, 4, 11, 5))
                .heartbeatTime(LocalDateTime.of(2026, 4, 4, 11, 1))
                .lastExecuteTime(LocalDateTime.of(2026, 4, 4, 11, 0))
                .errorMsg("timeout")
                .lastErrorCode("TimeoutException")
                .traceId("trace-001")
                .createTime(LocalDateTime.of(2026, 4, 4, 10, 0))
                .updateTime(LocalDateTime.of(2026, 4, 4, 10, 0))
                .finishTime(null)
                .build();

        ReliableTaskEntity entity = ReliableTaskConverter.toEntity(task);

        assertEquals(1L, entity.getId());
        assertEquals("CREATE_SHIPMENT", entity.getTaskType());
        assertEquals("ORDER", entity.getBizType());
        assertEquals("ORD-001", entity.getBizId());
        assertEquals("CREATE_SHIPMENT:ORDER:ORD-001", entity.getBizUniqueKey());
        assertEquals(0, entity.getStatus());
        assertEquals(3, entity.getPriority());
        assertEquals("{\"orderId\":\"ORD-001\"}", entity.getPayload());
        assertEquals(0, entity.getExecuteCount());
        assertEquals(7, entity.getVersion());
        assertEquals(3, entity.getMaxRetryCount());
        assertEquals("EXPONENTIAL", entity.getRetryStrategy());
        assertEquals(1000L, entity.getRetryIntervalMs());
        assertEquals(LocalDateTime.of(2026, 4, 4, 12, 0), entity.getNextExecuteTime());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 0), entity.getLockedAt());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 5), entity.getLockExpireAt());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 1), entity.getHeartbeatTime());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 0), entity.getLastExecuteTime());
        assertEquals("TimeoutException", entity.getLastErrorCode());
    }

    @Test
    @DisplayName("toEntity - null 输入返回 null")
    void toEntity_nullInput_returnsNull() {
        assertNull(ReliableTaskConverter.toEntity(null));
    }

    @Test
    @DisplayName("toDomain - Entity 转 TaskInstance")
    void toDomain_convertsAllFields() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setTaskType("CREATE_SHIPMENT");
        entity.setBizType("ORDER");
        entity.setBizId("ORD-001");
        entity.setBizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001");
        entity.setStatus(4);
        entity.setPriority(3);
        entity.setPayload("{\"orderId\":\"ORD-001\"}");
        entity.setExecuteCount(2);
        entity.setVersion(9);
        entity.setMaxRetryCount(3);
        entity.setRetryStrategy("EXPONENTIAL");
        entity.setRetryIntervalMs(1000L);
        entity.setNextExecuteTime(LocalDateTime.of(2026, 4, 4, 12, 0));
        entity.setShardKey("shard1");
        entity.setTenantId("tenant1");
        entity.setWorkerId("worker-1");
        entity.setLockedAt(LocalDateTime.of(2026, 4, 4, 11, 0));
        entity.setLockExpireAt(LocalDateTime.of(2026, 4, 4, 11, 5));
        entity.setHeartbeatTime(LocalDateTime.of(2026, 4, 4, 11, 1));
        entity.setLastExecuteTime(LocalDateTime.of(2026, 4, 4, 11, 0));
        entity.setErrorMsg("timeout");
        entity.setLastErrorCode("TimeoutException");
        entity.setTraceId("trace-001");
        entity.setCreateTime(LocalDateTime.of(2026, 4, 4, 10, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 4, 4, 10, 0));
        entity.setFinishTime(null);

        TaskInstance task = ReliableTaskConverter.toDomain(entity);

        assertEquals(1L, task.getId());
        assertEquals("CREATE_SHIPMENT", task.getTaskType());
        assertEquals(TaskStatus.RETRYING, task.getStatus());
        assertEquals(RetryStrategyType.EXPONENTIAL, task.getRetryStrategy());
        assertEquals(2, task.getExecuteCount());
        assertEquals(9, task.getVersion());
        assertEquals("timeout", task.getErrorMsg());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 0), task.getLockedAt());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 5), task.getLockExpireAt());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 1), task.getHeartbeatTime());
        assertEquals(LocalDateTime.of(2026, 4, 4, 11, 0), task.getLastExecuteTime());
        assertEquals("TimeoutException", task.getLastErrorCode());
    }

    @Test
    @DisplayName("toDomain - null 输入返回 null")
    void toDomain_nullInput_returnsNull() {
        assertNull(ReliableTaskConverter.toDomain(null));
    }

    @Test
    @DisplayName("toTaskVO - Entity 转 TaskVO")
    void toTaskVO_convertsListFields() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setTaskType("CREATE_SHIPMENT");
        entity.setBizType("ORDER");
        entity.setBizId("ORD-001");
        entity.setStatus(1);
        entity.setPriority(5);
        entity.setExecuteCount(1);
        entity.setMaxRetryCount(3);
        entity.setErrorMsg(null);
        entity.setWorkerId("worker-1");
        entity.setNextExecuteTime(LocalDateTime.of(2026, 4, 4, 12, 0));
        entity.setCreateTime(LocalDateTime.of(2026, 4, 4, 10, 0));

        TaskVO vo = ReliableTaskConverter.toTaskVO(entity);

        assertEquals(1L, vo.getId());
        assertEquals("CREATE_SHIPMENT", vo.getTaskType());
        assertEquals(1, vo.getStatusCode());
        assertEquals("执行中", vo.getStatusDesc());
        assertEquals(5, vo.getPriority());
    }

    @Test
    @DisplayName("toTaskDetailVO - Entity 转 TaskDetailVO")
    void toTaskDetailVO_convertsAllFields() {
        ReliableTaskEntity entity = new ReliableTaskEntity();
        entity.setId(1L);
        entity.setTaskType("CREATE_SHIPMENT");
        entity.setBizType("ORDER");
        entity.setBizId("ORD-001");
        entity.setBizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001");
        entity.setStatus(5);
        entity.setPriority(5);
        entity.setPayload("{\"orderId\":\"ORD-001\"}");
        entity.setExecuteCount(4);
        entity.setMaxRetryCount(3);
        entity.setRetryStrategy("EXPONENTIAL");
        entity.setRetryIntervalMs(1000L);
        entity.setErrorMsg("max retries exceeded");
        entity.setTraceId("trace-001");

        TaskDetailVO vo = ReliableTaskConverter.toTaskDetailVO(entity);

        assertEquals(1L, vo.getId());
        assertEquals(5, vo.getStatusCode());
        assertEquals("死亡/需人工干预", vo.getStatusDesc());
        assertEquals("EXPONENTIAL", vo.getRetryStrategy());
        assertEquals("{\"orderId\":\"ORD-001\"}", vo.getPayload());
    }

    @Test
    @DisplayName("toTaskLogVO - LogEntity 转 TaskLogVO")
    void toTaskLogVO_convertsAllFields() {
        ReliableTaskLogEntity entity = new ReliableTaskLogEntity();
        entity.setId(100L);
        entity.setTaskId(1L);
        entity.setExecuteTime(LocalDateTime.of(2026, 4, 4, 12, 0));
        entity.setDurationMs(1500L);
        entity.setStatus(3);
        entity.setErrorCode("TimeoutException");
        entity.setErrorMsg("connection timeout");
        entity.setWorkerId("worker-1");
        entity.setTraceId("trace-001");
        entity.setCreateTime(LocalDateTime.of(2026, 4, 4, 12, 0, 1));

        TaskLogVO vo = ReliableTaskConverter.toTaskLogVO(entity);

        assertEquals(100L, vo.getId());
        assertEquals(1L, vo.getTaskId());
        assertEquals(3, vo.getStatus());
        assertEquals("失败", vo.getStatusDesc());
        assertEquals(1500L, vo.getDurationMs());
        assertEquals("TimeoutException", vo.getErrorCode());
        assertEquals("connection timeout", vo.getErrorMsg());
        assertEquals("worker-1", vo.getWorkerId());
        assertEquals("trace-001", vo.getTraceId());
    }

    @Test
    @DisplayName("toTaskVOList - 批量转换")
    void toTaskVOList_batchConvert() {
        ReliableTaskEntity e1 = new ReliableTaskEntity();
        e1.setId(1L);
        e1.setTaskType("TYPE_A");
        e1.setStatus(0);

        ReliableTaskEntity e2 = new ReliableTaskEntity();
        e2.setId(2L);
        e2.setTaskType("TYPE_B");
        e2.setStatus(2);

        List<TaskVO> result = ReliableTaskConverter.toTaskVOList(List.of(e1, e2));

        assertEquals(2, result.size());
        assertEquals("TYPE_A", result.get(0).getTaskType());
        assertEquals("TYPE_B", result.get(1).getTaskType());
    }

    @Test
    @DisplayName("toTaskVOList - null 输入返回空列表")
    void toTaskVOList_nullInput_returnsEmptyList() {
        assertTrue(ReliableTaskConverter.toTaskVOList(null).isEmpty());
    }

    @Test
    @DisplayName("toTaskLogVOList - null 输入返回空列表")
    void toTaskLogVOList_nullInput_returnsEmptyList() {
        assertTrue(ReliableTaskConverter.toTaskLogVOList(null).isEmpty());
    }
}
