package com.reliabletask.core.spi;

import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("TaskStore v0.6 边界兼容测试")
class TaskStoreBoundaryCompatibilityTest {

    @Test
    @DisplayName("TaskStore - 旧宽接口实现可赋值给新窄接口")
    void taskStoreFacade_isAssignableToSplitStoreInterfaces() {
        LegacyCompatibleTaskStore store = new LegacyCompatibleTaskStore();

        TaskStore taskStore = store;
        TaskCommandStore commandStore = store;
        TaskQueryStore queryStore = store;
        TaskOperationsStore operationsStore = store;

        assertSame(store, taskStore);
        assertSame(store, commandStore);
        assertSame(store, queryStore);
        assertSame(store, operationsStore);
    }

    @Test
    @DisplayName("TaskStore - 兼容门面自身不声明额外抽象方法")
    void taskStoreFacade_declaresNoAdditionalAbstractMethods() {
        long declaredAbstractMethods = List.of(TaskStore.class.getDeclaredMethods()).stream()
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .count();

        assertEquals(0, declaredAbstractMethods);
    }

    private static class LegacyCompatibleTaskStore implements TaskStore {

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
            return false;
        }

        @Override
        public boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime) {
            return false;
        }

        @Override
        public boolean markDead(Long id, String errorMsg) {
            return false;
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
            return false;
        }

        @Override
        public void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                            boolean success, long durationMs, String errorCode, String errorMessage,
                            String workerId, String traceId) {
            // no-op
        }

        @Override
        public PageResult<TaskVO> listTasks(TaskQueryRequest request) {
            return PageResult.of(List.of(), 0, 1, 10);
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
