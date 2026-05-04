package com.reliabletask.executor.handler;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import com.reliabletask.executor.interceptor.TaskExecutionInterceptor;
import com.reliabletask.executor.retry.RetryEngine;
import com.reliabletask.executor.template.TransactionAwareTaskTemplate;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.executor.threadpool.ThreadPoolProperties;
import com.reliabletask.executor.worker.WorkerProperties;
import com.reliabletask.executor.worker.WorkerScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReliableTask V1.5 最小集成测试")
class ReliableTaskV15IntegrationTest {

    private InMemoryTaskStore taskStore;
    private TaskExecutorFactory executorFactory;

    @BeforeEach
    void setUp() {
        taskStore = new InMemoryTaskStore();
        ThreadPoolProperties properties = new ThreadPoolProperties();
        properties.setDefaultCoreSize(1);
        properties.setDefaultMaxSize(1);
        properties.setDefaultQueueCapacity(20);
        properties.setKeepAliveSeconds(1);
        executorFactory = new TaskExecutorFactory(properties);
    }

    @AfterEach
    void tearDown() {
        executorFactory.shutdown();
        TraceContext.clear();
    }

    @Test
    @DisplayName("投递 -> Worker 抢占 -> Handler 成功 -> SUCCESS")
    void submitThenWorkerExecuteSuccess() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        AtomicInteger executions = new AtomicInteger();
        registry.registerHandler(new CountingHandler("SUCCESS_TASK", executions));

        TraceContext.setTraceId("trace-success");
        TaskTemplate taskTemplate = new TransactionAwareTaskTemplate(taskStore);
        String taskId = taskTemplate.submit(TaskSubmitRequest.builder()
                .taskType("SUCCESS_TASK")
                .bizType("ORDER")
                .bizId("ORD-1")
                .payload("{}")
                .build());
        TraceContext.clear();

        newWorker(registry).pollAndExecute();

        TaskInstance task = taskStore.getById(Long.valueOf(taskId));
        assertEquals(TaskStatus.SUCCESS, task.getStatus());
        assertEquals(1, executions.get());
        assertEquals(1, taskStore.logs.size());
        assertEquals("trace-success", taskStore.logs.get(0).traceId);
        assertFalse(taskStore.logs.get(0).workerId.isBlank());
    }

    @Test
    @DisplayName("失败 -> RETRYING -> 到期后再次执行 -> SUCCESS")
    void retryingTaskCanBeClaimedAndExecutedAgain() throws Exception {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        AtomicInteger executions = new AtomicInteger();
        registry.registerHandler(new FailOnceHandler("RETRY_TASK", executions));

        TaskTemplate taskTemplate = new TransactionAwareTaskTemplate(taskStore);
        String taskId = taskTemplate.submit(TaskSubmitRequest.builder()
                .taskType("RETRY_TASK")
                .bizType("ORDER")
                .bizId("ORD-2")
                .payload("{}")
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(1L)
                .build());

        WorkerScheduler worker = newWorker(registry);
        worker.pollAndExecute();

        TaskInstance retrying = taskStore.getById(Long.valueOf(taskId));
        assertEquals(TaskStatus.RETRYING, retrying.getStatus());
        assertEquals(1, retrying.getExecuteCount());

        Thread.sleep(20L);
        worker.pollAndExecute();

        TaskInstance success = taskStore.getById(Long.valueOf(taskId));
        assertEquals(TaskStatus.SUCCESS, success.getStatus());
        assertEquals(2, success.getExecuteCount());
        assertEquals(2, executions.get());
    }

    @Test
    @DisplayName("多 Worker 并发抢占同一任务只有一个成功")
    void concurrentClaimOnlyOneWorkerSucceeds() throws Exception {
        TaskInstance task = taskStore.save(TaskInstance.builder()
                .taskType("CLAIM_TASK")
                .bizType("ORDER")
                .bizId("ORD-3")
                .bizUniqueKey("CLAIM_TASK:ORDER:ORD-3")
                .status(TaskStatus.PENDING)
                .priority(5)
                .payload("{}")
                .executeCount(0)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(1L)
                .nextExecuteTime(LocalDateTime.now())
                .build());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> claims = List.of(
                    () -> taskStore.claimTask(task.getId(), "worker-a"),
                    () -> taskStore.claimTask(task.getId(), "worker-b")
            );
            List<Future<Boolean>> futures = pool.invokeAll(claims);
            long successCount = futures.stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .count();

            assertEquals(1L, successCount);
            assertEquals(TaskStatus.RUNNING, taskStore.getById(task.getId()).getStatus());
            assertEquals(1, taskStore.getById(task.getId()).getExecuteCount());
        } finally {
            pool.shutdownNow();
        }
    }

    private WorkerScheduler newWorker(TaskHandlerRegistry registry) {
        TaskExecutor taskExecutor = new TaskExecutor(taskStore, registry, executorFactory,
                new RetryEngine(taskStore), new TaskExecutionInterceptor());
        WorkerProperties workerProperties = new WorkerProperties();
        workerProperties.setEnabled(true);
        workerProperties.setBatchSize(10);
        workerProperties.setPollIntervalMs(1L);
        return new WorkerScheduler(taskStore, workerProperties, taskExecutor);
    }

    private static class CountingHandler implements TaskHandler {
        private final String taskType;
        private final AtomicInteger executions;

        private CountingHandler(String taskType, AtomicInteger executions) {
            this.taskType = taskType;
            this.executions = executions;
        }

        @Override
        public String getTaskType() {
            return taskType;
        }

        @Override
        public void execute(TaskInstance task) {
            executions.incrementAndGet();
        }
    }

    private static class FailOnceHandler extends CountingHandler {
        private FailOnceHandler(String taskType, AtomicInteger executions) {
            super(taskType, executions);
        }

        @Override
        public void execute(TaskInstance task) {
            super.execute(task);
            if (task.getExecuteCount() == 1) {
                throw new IllegalStateException("temporary failure");
            }
        }
    }

    private static class ExecutionLog {
        private final Long taskId;
        private final int executeNo;
        private final String statusBefore;
        private final String statusAfter;
        private final boolean success;
        private final String errorCode;
        private final String workerId;
        private final String traceId;

        private ExecutionLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                             boolean success, String errorCode, String workerId, String traceId) {
            this.taskId = taskId;
            this.executeNo = executeNo;
            this.statusBefore = statusBefore;
            this.statusAfter = statusAfter;
            this.success = success;
            this.errorCode = errorCode;
            this.workerId = workerId;
            this.traceId = traceId;
        }
    }

    private static class InMemoryTaskStore implements TaskStore {
        private final Map<Long, TaskInstance> tasks = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<ExecutionLog> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicInteger idGenerator = new AtomicInteger();

        @Override
        public synchronized TaskInstance save(TaskInstance task) {
            Optional<TaskInstance> existing = tasks.values().stream()
                    .filter(item -> Objects.equals(item.getBizUniqueKey(), task.getBizUniqueKey()))
                    .findFirst();
            if (task.getBizUniqueKey() != null && existing.isPresent()) {
                return copy(existing.get());
            }

            TaskInstance saved = copy(task);
            saved.setId((long) idGenerator.incrementAndGet());
            if (saved.getStatus() == null) {
                saved.setStatus(TaskStatus.PENDING);
            }
            if (saved.getExecuteCount() == null) {
                saved.setExecuteCount(0);
            }
            if (saved.getNextExecuteTime() == null) {
                saved.setNextExecuteTime(LocalDateTime.now());
            }
            tasks.put(saved.getId(), saved);
            return copy(saved);
        }

        @Override
        public TaskInstance getById(Long id) {
            return copy(tasks.get(id));
        }

        @Override
        public TaskInstance getByBizUniqueKey(String bizUniqueKey) {
            return tasks.values().stream()
                    .filter(task -> Objects.equals(task.getBizUniqueKey(), bizUniqueKey))
                    .findFirst()
                    .map(InMemoryTaskStore::copy)
                    .orElse(null);
        }

        @Override
        public List<TaskInstance> fetchPendingTasks(int batchSize) {
            LocalDateTime now = LocalDateTime.now();
            return tasks.values().stream()
                    .filter(task -> task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RETRYING)
                    .filter(task -> task.getNextExecuteTime() == null || !task.getNextExecuteTime().isAfter(now))
                    .sorted(Comparator.comparing(TaskInstance::getPriority, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(TaskInstance::getNextExecuteTime, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(TaskInstance::getId))
                    .limit(batchSize)
                    .map(InMemoryTaskStore::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public synchronized boolean claimTask(Long id, String workerId) {
            TaskInstance task = tasks.get(id);
            if (task == null) {
                return false;
            }
            boolean executable = task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RETRYING;
            if (!executable || task.getNextExecuteTime().isAfter(LocalDateTime.now())) {
                return false;
            }
            task.setStatus(TaskStatus.RUNNING);
            task.setWorkerId(workerId);
            task.setLockedAt(LocalDateTime.now());
            task.setLockExpireAt(LocalDateTime.now().plusMinutes(5));
            task.setLastExecuteTime(LocalDateTime.now());
            task.setExecuteCount(task.getExecuteCount() + 1);
            return true;
        }

        @Override
        public synchronized boolean markSuccess(Long id) {
            TaskInstance task = tasks.get(id);
            if (task == null || task.getStatus() != TaskStatus.RUNNING) {
                return false;
            }
            task.setStatus(TaskStatus.SUCCESS);
            task.setFinishTime(LocalDateTime.now());
            return true;
        }

        @Override
        public synchronized boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime) {
            return markWaitRetry(id, null, errorMsg, nextExecuteTime);
        }

        @Override
        public synchronized boolean markWaitRetry(Long id, String errorCode, String errorMsg,
                                                  LocalDateTime nextExecuteTime) {
            TaskInstance task = tasks.get(id);
            if (task == null || task.getStatus() != TaskStatus.RUNNING) {
                return false;
            }
            task.setStatus(TaskStatus.RETRYING);
            task.setErrorMsg(errorMsg);
            task.setLastErrorCode(errorCode);
            task.setNextExecuteTime(nextExecuteTime);
            task.setWorkerId(null);
            task.setLockedAt(null);
            task.setLockExpireAt(null);
            return true;
        }

        @Override
        public synchronized boolean markDead(Long id, String errorMsg) {
            return markDead(id, null, errorMsg);
        }

        @Override
        public synchronized boolean markDead(Long id, String errorCode, String errorMsg) {
            TaskInstance task = tasks.get(id);
            if (task == null) {
                return false;
            }
            task.setStatus(TaskStatus.DEAD);
            task.setErrorMsg(errorMsg);
            task.setLastErrorCode(errorCode);
            task.setFinishTime(LocalDateTime.now());
            return true;
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
            logs.add(new ExecutionLog(taskId, executeNo, statusBefore, statusAfter,
                    success, errorCode, workerId, traceId));
        }

        @Override
        public PageResult<TaskVO> listTasks(TaskQueryRequest request) {
            return PageResult.of(List.of(), 0, request.getPageNum(), request.getPageSize());
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
            return new TaskStatsVO();
        }

        private static TaskInstance copy(TaskInstance task) {
            if (task == null) {
                return null;
            }
            return TaskInstance.builder()
                    .id(task.getId())
                    .taskType(task.getTaskType())
                    .bizType(task.getBizType())
                    .bizId(task.getBizId())
                    .bizUniqueKey(task.getBizUniqueKey())
                    .status(task.getStatus())
                    .priority(task.getPriority())
                    .payload(task.getPayload())
                    .executeCount(task.getExecuteCount())
                    .version(task.getVersion())
                    .maxRetryCount(task.getMaxRetryCount())
                    .retryStrategy(task.getRetryStrategy())
                    .retryIntervalMs(task.getRetryIntervalMs())
                    .nextExecuteTime(task.getNextExecuteTime())
                    .shardKey(task.getShardKey())
                    .tenantId(task.getTenantId())
                    .workerId(task.getWorkerId())
                    .lockedAt(task.getLockedAt())
                    .lockExpireAt(task.getLockExpireAt())
                    .heartbeatTime(task.getHeartbeatTime())
                    .lastExecuteTime(task.getLastExecuteTime())
                    .errorMsg(task.getErrorMsg())
                    .lastErrorCode(task.getLastErrorCode())
                    .traceId(task.getTraceId())
                    .createTime(task.getCreateTime())
                    .updateTime(task.getUpdateTime())
                    .finishTime(task.getFinishTime())
                    .build();
        }
    }
}
