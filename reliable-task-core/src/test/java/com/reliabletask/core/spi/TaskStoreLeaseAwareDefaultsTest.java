package com.reliabletask.core.spi;

import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskStore 租约感知默认方法测试")
class TaskStoreLeaseAwareDefaultsTest {

    @Test
    @DisplayName("TaskExecutionLease - 可从任务实例提取抢占信息")
    void taskExecutionLeaseFromTask_extractsClaimFields() {
        LocalDateTime lockedAt = LocalDateTime.now().minusSeconds(5);
        LocalDateTime lockExpireAt = LocalDateTime.now().plusMinutes(5);
        TaskInstance task = TaskInstance.builder()
                .id(10L)
                .workerId("worker-1")
                .lockedAt(lockedAt)
                .lockExpireAt(lockExpireAt)
                .version(7)
                .build();

        TaskExecutionLease lease = TaskExecutionLease.from(task);

        assertEquals(10L, lease.getTaskId());
        assertEquals("worker-1", lease.getWorkerId());
        assertSame(lockedAt, lease.getLockedAt());
        assertSame(lockExpireAt, lease.getLockExpireAt());
        assertEquals(7, lease.getVersion());
        assertNull(TaskExecutionLease.from(null));
    }

    @Test
    @DisplayName("TaskStore - 租约成功回写默认委托旧接口")
    void markSuccessWithLease_delegatesToLegacyMethod() {
        RecordingTaskStore store = new RecordingTaskStore();
        TaskExecutionLease lease = TaskExecutionLease.builder().taskId(1L).workerId("worker-1").version(2).build();
        store.markSuccessResult = true;

        assertTrue(store.markSuccess(lease));

        assertEquals(1L, store.markSuccessId);
    }

    @Test
    @DisplayName("TaskStore - 租约重试和死信默认委托旧接口")
    void failureStateUpdatesWithLease_delegateToLegacyMethods() {
        RecordingTaskStore store = new RecordingTaskStore();
        LocalDateTime nextExecuteTime = LocalDateTime.now().plusSeconds(30);
        TaskExecutionLease lease = TaskExecutionLease.builder().taskId(2L).workerId("worker-2").version(3).build();
        store.markWaitRetryResult = true;
        store.markDeadResult = true;

        assertTrue(store.markWaitRetry(lease, "ERR", "retry", nextExecuteTime));
        assertTrue(store.markDead(lease, "ERR", "dead"));

        assertEquals(2L, store.markWaitRetryId);
        assertEquals("retry", store.markWaitRetryErrorMsg);
        assertSame(nextExecuteTime, store.markWaitRetryNextExecuteTime);
        assertEquals(2L, store.markDeadId);
        assertEquals("dead", store.markDeadErrorMsg);
    }

    @Test
    @DisplayName("TaskStore - 租约恢复默认委托旧接口")
    void resetTimeoutTaskWithLease_delegatesToLegacyMethod() {
        RecordingTaskStore store = new RecordingTaskStore();
        TaskExecutionLease lease = TaskExecutionLease.builder().taskId(3L).workerId("worker-3").version(4).build();
        store.resetTimeoutTaskResult = true;

        assertTrue(store.resetTimeoutTask(lease));

        assertEquals(3L, store.resetTimeoutTaskId);
    }

    @Test
    @DisplayName("TaskStore - 空租约不委托旧接口")
    void leaseAwareDefaults_withoutTaskIdReturnFalse() {
        RecordingTaskStore store = new RecordingTaskStore();
        TaskExecutionLease missingTaskId = TaskExecutionLease.builder().workerId("worker-1").build();

        assertFalse(store.markSuccess((TaskExecutionLease) null));
        assertFalse(store.markSuccess(missingTaskId));
        assertFalse(store.resetTimeoutTask(missingTaskId));

        assertEquals(0, store.legacyUpdateCalls);
    }

    private static class RecordingTaskStore implements TaskStore {

        private boolean markSuccessResult;
        private boolean markWaitRetryResult;
        private boolean markDeadResult;
        private boolean resetTimeoutTaskResult;
        private int legacyUpdateCalls;
        private Long markSuccessId;
        private Long markWaitRetryId;
        private String markWaitRetryErrorMsg;
        private LocalDateTime markWaitRetryNextExecuteTime;
        private Long markDeadId;
        private String markDeadErrorMsg;
        private Long resetTimeoutTaskId;

        @Override
        public TaskInstance save(TaskInstance task) {
            return task;
        }

        @Override
        public TaskInstance getById(Long id) {
            return null;
        }

        @Override
        public TaskInstance getByBizUniqueKey(String bizUniqueKey) {
            return null;
        }

        @Override
        public List<TaskInstance> fetchPendingTasks(int batchSize) {
            return List.of();
        }

        @Override
        public boolean claimTask(Long id, String workerId) {
            return false;
        }

        @Override
        public boolean markSuccess(Long id) {
            legacyUpdateCalls++;
            markSuccessId = id;
            return markSuccessResult;
        }

        @Override
        public boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime) {
            legacyUpdateCalls++;
            markWaitRetryId = id;
            markWaitRetryErrorMsg = errorMsg;
            markWaitRetryNextExecuteTime = nextExecuteTime;
            return markWaitRetryResult;
        }

        @Override
        public boolean markDead(Long id, String errorMsg) {
            legacyUpdateCalls++;
            markDeadId = id;
            markDeadErrorMsg = errorMsg;
            return markDeadResult;
        }

        @Override
        public boolean cancelTask(Long id) {
            return false;
        }

        @Override
        public boolean requeueTask(Long id) {
            return false;
        }

        @Override
        public boolean updatePayload(Long id, String payload) {
            return false;
        }

        @Override
        public List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold) {
            return List.of();
        }

        @Override
        public boolean resetTimeoutTask(Long id) {
            legacyUpdateCalls++;
            resetTimeoutTaskId = id;
            return resetTimeoutTaskResult;
        }

        @Override
        public void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                            boolean success, long durationMs, String errorCode, String errorMessage,
                            String workerId, String traceId) {
            // no-op for default method tests
        }

        @Override
        public PageResult<TaskVO> listTasks(TaskQueryRequest request) {
            return null;
        }

        @Override
        public TaskDetailVO getTaskDetail(Long id) {
            return null;
        }

        @Override
        public List<TaskLogVO> getTaskLogs(Long taskId) {
            return List.of();
        }

        @Override
        public TaskStatsVO getStats() {
            return null;
        }
    }
}
