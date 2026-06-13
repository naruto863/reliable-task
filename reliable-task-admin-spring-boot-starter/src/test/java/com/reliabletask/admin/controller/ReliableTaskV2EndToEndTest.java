package com.reliabletask.admin.controller;

import com.reliabletask.admin.model.Result;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;
import com.reliabletask.executor.handler.TaskExecutor;
import com.reliabletask.executor.handler.TaskHandlerRegistry;
import com.reliabletask.executor.interceptor.TaskExecutionInterceptor;
import com.reliabletask.executor.retry.RetryEngine;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;
import com.reliabletask.executor.template.TransactionAwareTaskTemplate;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.executor.threadpool.ThreadPoolProperties;
import com.reliabletask.executor.worker.WorkerProperties;
import com.reliabletask.executor.worker.WorkerScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReliableTask Admin starter V2 最小端到端验证")
class ReliableTaskV2EndToEndTest {

    private TestHarness harness;

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.executor.shutdown();
            harness.executorFactory.shutdown();
        }
    }

    @Test
    @DisplayName("starter 默认链路协同完成 V2 最小能力闭环")
    void starterDefaultPath_coversV2MinimumCapabilities() {
        harness = new TestHarness();
        ShipmentHandler shipmentHandler = new ShipmentHandler();
        RetryOnceHandler retryOnceHandler = new RetryOnceHandler();
        registerHandler(harness.registry, shipmentHandler);
        registerHandler(harness.registry, retryOnceHandler);

        TaskSubmitRequest shipmentRequest = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-V2-001")
                .payloadObject(new ShipmentPayload("ORD-V2-001", 2))
                .build();
        TaskSubmitResult firstSubmit = harness.taskTemplate.submitForResult(shipmentRequest);
        TaskSubmitResult duplicateSubmit = harness.taskTemplate.submitForResult(shipmentRequest);

        assertThat(firstSubmit.isCreated()).isTrue();
        assertThat(duplicateSubmit.isExisting()).isTrue();
        assertThat(duplicateSubmit.getTaskId()).isEqualTo(firstSubmit.getTaskId());

        harness.worker.reportHeartbeat();
        assertThat(harness.taskStore.workers).hasSize(1);

        harness.worker.pollAndExecute();

        TaskInstance shipmentTask = harness.taskStore.getById(firstSubmit.getTaskId());
        assertThat(shipmentTask.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(shipmentTask.getHeartbeatTime()).isNotNull();
        assertThat(shipmentTask.getLockExpireAt()).isNotNull();
        assertThat(shipmentHandler.payload.get()).isEqualTo(new ShipmentPayload("ORD-V2-001", 2));
        assertThat(harness.taskStore.lastFetchSize).isEqualTo(1);
        assertThat(harness.metrics.events)
                .anySatisfy(event -> assertThat(event.getStatus()).isEqualTo(TaskStatus.SUCCESS));

        Long retryTaskId = harness.taskTemplate.submitForResult(TaskSubmitRequest.builder()
                .taskType("RETRY_ONCE")
                .bizType("ORDER")
                .bizId("ORD-V2-RETRY")
                .payload("{}")
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(1000L)
                .build()).getTaskId();

        harness.worker.pollAndExecute();

        TaskInstance retryingTask = harness.taskStore.getById(retryTaskId);
        assertThat(retryingTask.getStatus()).isEqualTo(TaskStatus.RETRYING);
        assertThat(harness.metrics.events)
                .anySatisfy(event -> assertThat(event.getStatus()).isEqualTo(TaskStatus.RETRYING));
        assertThat(harness.taskStore.auditLogs)
                .anySatisfy(log -> assertThat(log.getOperationType()).isEqualTo("SYSTEM_RETRY_SCHEDULED"));

        Long cancelTaskId = harness.taskTemplate.submitForResult(TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-V2-CANCEL")
                .payload("{}")
                .build()).getTaskId();
        Result<Boolean> cancelResult = harness.adminController.cancel(cancelTaskId, "ops-user", "trace-cancel");

        assertThat(cancelResult.getCode()).isEqualTo(200);
        assertThat(harness.taskStore.getById(cancelTaskId).getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(harness.taskStore.auditLogs)
                .anySatisfy(log -> {
                    assertThat(log.getOperationType()).isEqualTo("TASK_CANCEL");
                    assertThat(log.getOperator()).isEqualTo("ops-user");
                    assertThat(log.getTraceId()).isEqualTo("trace-cancel");
                });

        TaskInstance deadA = harness.taskStore.save(deadTask("DEAD-A"));
        TaskInstance deadB = harness.taskStore.save(deadTask("DEAD-B"));
        Result<BatchOperationResult> batchResult = harness.adminController.batchRequeue(
                new TaskAdminController.BatchOperationRequest(null, null, null, null, 10, false),
                "ops-user", "trace-batch");

        assertThat(batchResult.getCode()).isEqualTo(200);
        assertThat(batchResult.getData().getTotalCount()).isEqualTo(2);
        assertThat(batchResult.getData().getSuccessCount()).isEqualTo(2);
        assertThat(harness.taskStore.getById(deadA.getId()).getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(harness.taskStore.getById(deadB.getId()).getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(harness.taskStore.batchResults).containsKey(batchResult.getData().getBatchOperationId());
        assertThat(harness.taskStore.auditLogs)
                .anySatisfy(log -> assertThat(log.getOperationType()).isEqualTo("BATCH_REQUEUE_DEAD"));
    }

    private static TaskInstance deadTask(String bizId) {
        return TaskInstance.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId(bizId)
                .bizUniqueKey("CREATE_SHIPMENT:ORDER:" + bizId)
                .status(TaskStatus.DEAD)
                .priority(5)
                .payload("{}")
                .executeCount(1)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.FIXED)
                .retryIntervalMs(1000L)
                .nextExecuteTime(LocalDateTime.now())
                .build();
    }

    private static void registerHandler(TaskHandlerRegistry registry, TaskHandler handler) {
        try {
            java.lang.reflect.Method method = TaskHandlerRegistry.class
                    .getDeclaredMethod("registerHandler", TaskHandler.class);
            method.setAccessible(true);
            method.invoke(registry, handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TestHarness {
        private final InMemoryTaskStore taskStore = new InMemoryTaskStore();
        private final RecordingMetricsRecorder metrics = new RecordingMetricsRecorder();
        private final TaskPayloadSerializer serializer = new JacksonTaskPayloadSerializer();
        private final TaskHandlerRegistry registry = new TaskHandlerRegistry();
        private final WorkerProperties workerProperties = new WorkerProperties();
        private final TaskExecutorFactory executorFactory;
        private final TaskExecutor executor;
        private final WorkerScheduler worker;
        private final TaskTemplate taskTemplate;
        private final TaskAdminController adminController;

        private TestHarness() {
            ThreadPoolProperties threadPoolProperties = new ThreadPoolProperties();
            threadPoolProperties.setDefaultCoreSize(1);
            threadPoolProperties.setDefaultMaxSize(1);
            threadPoolProperties.setDefaultQueueCapacity(20);
            threadPoolProperties.setKeepAliveSeconds(1);
            executorFactory = new TaskExecutorFactory(threadPoolProperties);

            workerProperties.setEnabled(true);
            workerProperties.setBatchSize(10);
            workerProperties.setBackpressureEnabled(true);
            workerProperties.setBackpressureMinFetchSize(1);
            workerProperties.setBackpressureMaxFetchSize(1);
            workerProperties.setHeartbeatEnabled(true);
            workerProperties.setHeartbeatIntervalMs(1000L);
            workerProperties.setLockRenewalTtlSeconds(60L);

            RetryEngine retryEngine = new RetryEngine(taskStore, metrics, taskStore::saveAuditLog);
            executor = new TaskExecutor(taskStore, registry, executorFactory, retryEngine,
                    new TaskExecutionInterceptor(), serializer, workerProperties, metrics, taskStore::saveAuditLog);
            worker = new WorkerScheduler(taskStore, workerProperties, executor);
            List<IdempotencyStrategy> strategies = List.of(
                    new StrictUniqueIdempotencyStrategy(),
                    new AllowAfterTerminalIdempotencyStrategy());
            taskTemplate = new TransactionAwareTaskTemplate(taskStore, strategies,
                    StrictUniqueIdempotencyStrategy.NAME, serializer, metrics);
            adminController = new TaskAdminController(taskStore, true,
                    (operator, action, taskId) -> action.equals("TASK_CANCEL")
                            || action.equals("TASK_BATCH_OPERATION"));
        }
    }

    private record ShipmentPayload(String orderNo, int quantity) {
    }

    private static final class ShipmentHandler implements TaskHandler {
        private final AtomicReference<ShipmentPayload> payload = new AtomicReference<>();

        @Override
        public String getTaskType() {
            return "CREATE_SHIPMENT";
        }

        @Override
        public Class<?> payloadType() {
            return ShipmentPayload.class;
        }

        @Override
        public void execute(TaskInstance task) {
            throw new UnsupportedOperationException("typed payload is required");
        }

        @Override
        public void execute(TaskInstance task, Object payload) {
            this.payload.set((ShipmentPayload) payload);
        }
    }

    private static final class RetryOnceHandler implements TaskHandler {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public String getTaskType() {
            return "RETRY_ONCE";
        }

        @Override
        public void execute(TaskInstance task) {
            executions.incrementAndGet();
            throw new IllegalStateException("temporary failure");
        }
    }

    private static final class RecordingMetricsRecorder implements TaskMetricsRecorder {
        private final List<TaskExecutionMetricsEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void record(TaskExecutionMetricsEvent event) {
            events.add(event);
        }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private final Map<Long, TaskInstance> tasks = new ConcurrentHashMap<>();
        private final Map<String, WorkerHeartbeat> workers = new ConcurrentHashMap<>();
        private final List<AuditLog> auditLogs = new CopyOnWriteArrayList<>();
        private final Map<Long, BatchOperationResult> batchResults = new ConcurrentHashMap<>();
        private final AtomicInteger idGenerator = new AtomicInteger();
        private final AtomicInteger batchIdGenerator = new AtomicInteger();
        private volatile int lastFetchSize;

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
            lastFetchSize = batchSize;
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
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(TaskStatus.RUNNING);
            task.setWorkerId(workerId);
            task.setLockedAt(now);
            task.setLockExpireAt(now.plusMinutes(5));
            task.setLastExecuteTime(now);
            task.setExecuteCount(task.getExecuteCount() + 1);
            return true;
        }

        @Override
        public synchronized boolean renewTaskLease(Long id, String workerId,
                                                   LocalDateTime heartbeatTime,
                                                   LocalDateTime lockExpireAt) {
            TaskInstance task = tasks.get(id);
            if (task == null || task.getStatus() != TaskStatus.RUNNING
                    || !Objects.equals(task.getWorkerId(), workerId)) {
                return false;
            }
            task.setHeartbeatTime(heartbeatTime);
            task.setLockExpireAt(lockExpireAt);
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
            releaseLease(task);
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
            releaseLease(task);
            return true;
        }

        @Override
        public synchronized boolean cancelTask(Long id) {
            TaskInstance task = tasks.get(id);
            if (task == null || isTerminal(task.getStatus())) {
                return false;
            }
            task.setStatus(TaskStatus.CANCELLED);
            task.setFinishTime(LocalDateTime.now());
            releaseLease(task);
            return true;
        }

        @Override
        public synchronized boolean requeueTask(Long id) {
            TaskInstance task = tasks.get(id);
            if (task == null || (task.getStatus() != TaskStatus.DEAD && task.getStatus() != TaskStatus.CANCELLED)) {
                return false;
            }
            task.setStatus(TaskStatus.PENDING);
            task.setErrorMsg(null);
            task.setLastErrorCode(null);
            task.setExecuteCount(0);
            task.setNextExecuteTime(LocalDateTime.now());
            task.setFinishTime(null);
            releaseLease(task);
            return true;
        }

        @Override
        public synchronized boolean updatePayload(Long id, String payload) {
            TaskInstance task = tasks.get(id);
            if (task == null || (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.RETRYING)) {
                return false;
            }
            task.setPayload(payload);
            return true;
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
            // The end-to-end assertions use final task state, metrics, and audit events.
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
            TaskStatsVO stats = new TaskStatsVO();
            Map<Integer, Long> statusCount = tasks.values().stream()
                    .collect(Collectors.groupingBy(task -> task.getStatus().getCode(), Collectors.counting()));
            stats.setStatusCount(statusCount);
            stats.setPendingTasks(statusCount.getOrDefault(TaskStatus.PENDING.getCode(), 0L));
            stats.setDeadTasks(statusCount.getOrDefault(TaskStatus.DEAD.getCode(), 0L));
            return stats;
        }

        @Override
        public void reportWorkerHeartbeat(WorkerHeartbeat heartbeat) {
            workers.put(heartbeat.getWorkerId(), heartbeat);
        }

        @Override
        public void saveAuditLog(AuditLog auditLog) {
            auditLogs.add(auditLog);
        }

        @Override
        public List<AuditLog> getAuditLogsByTaskId(Long taskId) {
            return auditLogs.stream()
                    .filter(log -> Objects.equals(log.getTaskId(), taskId))
                    .toList();
        }

        @Override
        public PageResult<AuditLog> listAuditLogs(String operator, LocalDateTime createTimeStart,
                                                  LocalDateTime createTimeEnd, int pageNum, int pageSize) {
            List<AuditLog> filtered = auditLogs.stream()
                    .filter(log -> operator == null || Objects.equals(log.getOperator(), operator))
                    .toList();
            return PageResult.of(filtered, filtered.size(), pageNum, pageSize);
        }

        @Override
        public Long createBatchOperation(String operationType, String operator, String taskType,
                                         TaskStatus taskStatus, LocalDateTime createTimeStart,
                                         LocalDateTime createTimeEnd, int limit, boolean dryRun,
                                         String requestCondition, String traceId) {
            Long id = (long) batchIdGenerator.incrementAndGet();
            batchResults.put(id, BatchOperationResult.builder()
                    .batchOperationId(id)
                    .totalCount(0)
                    .successCount(0)
                    .failCount(0)
                    .failedTaskIds(List.of())
                    .dryRun(dryRun)
                    .success(true)
                    .build());
            return id;
        }

        @Override
        public boolean updateBatchOperationResult(BatchOperationResult result) {
            batchResults.put(result.getBatchOperationId(), result);
            return true;
        }

        @Override
        public BatchOperationResult getBatchOperationResult(Long batchOperationId) {
            return batchResults.get(batchOperationId);
        }

        @Override
        public List<Long> findOperableTaskIds(String taskType, TaskStatus status,
                                              LocalDateTime createTimeStart,
                                              LocalDateTime createTimeEnd,
                                              int limit) {
            return tasks.values().stream()
                    .filter(task -> taskType == null || Objects.equals(task.getTaskType(), taskType))
                    .filter(task -> status == null || task.getStatus() == status)
                    .filter(task -> createTimeStart == null || task.getCreateTime() == null
                            || !task.getCreateTime().isBefore(createTimeStart))
                    .filter(task -> createTimeEnd == null || task.getCreateTime() == null
                            || !task.getCreateTime().isAfter(createTimeEnd))
                    .sorted(Comparator.comparing(TaskInstance::getId))
                    .limit(limit)
                    .map(TaskInstance::getId)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        private static boolean isTerminal(TaskStatus status) {
            return status == TaskStatus.SUCCESS || status == TaskStatus.DEAD || status == TaskStatus.CANCELLED;
        }

        private static void releaseLease(TaskInstance task) {
            task.setWorkerId(null);
            task.setLockedAt(null);
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
