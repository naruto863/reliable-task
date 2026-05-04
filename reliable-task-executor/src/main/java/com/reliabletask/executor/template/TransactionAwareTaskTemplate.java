package com.reliabletask.executor.template;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import lombok.extern.slf4j.Slf4j;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事务感知的任务投递模板实现
 *
 * <p>核心能力: 任务与业务数据共用 Spring 本地事务。
 * 业务方法在事务中调用 submit() 时，任务记录会在当前事务内直接写入；
 * 业务事务回滚时，任务记录随业务数据一起回滚。
 *
 * <p>状态流转说明:
 * <pre>
 *   业务调用 submit() → 构建 TaskInstance(status=PENDING)
 *   → TaskStore.save() → PENDING 状态入库
 * </pre>
 */
@Slf4j
public class TransactionAwareTaskTemplate implements TaskTemplate {

    private final TaskStore taskStore;
    private final Map<String, IdempotencyStrategy> idempotencyStrategies;
    private final String defaultIdempotencyStrategyName;
    private final TaskPayloadSerializer payloadSerializer;
    private final TaskMetricsRecorder metricsRecorder;

    public TransactionAwareTaskTemplate(TaskStore taskStore) {
        this(taskStore, List.of(new StrictUniqueIdempotencyStrategy()), StrictUniqueIdempotencyStrategy.NAME,
                new JacksonTaskPayloadSerializer());
    }

    public TransactionAwareTaskTemplate(TaskStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName, new JacksonTaskPayloadSerializer());
    }

    public TransactionAwareTaskTemplate(TaskStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadSerializer payloadSerializer) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                payloadSerializer, new NoopTaskMetricsRecorder());
    }

    public TransactionAwareTaskTemplate(TaskStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadSerializer payloadSerializer,
                                        TaskMetricsRecorder metricsRecorder) {
        this.taskStore = taskStore;
        this.idempotencyStrategies = new HashMap<>();
        if (idempotencyStrategies != null) {
            for (IdempotencyStrategy strategy : idempotencyStrategies) {
                this.idempotencyStrategies.put(strategy.getName(), strategy);
            }
        }
        this.defaultIdempotencyStrategyName = defaultIdempotencyStrategyName;
        this.payloadSerializer = payloadSerializer != null ? payloadSerializer : new JacksonTaskPayloadSerializer();
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new NoopTaskMetricsRecorder();
    }

    @Override
    public String submit(TaskSubmitRequest request) {
        return submitForResult(request).getResultId();
    }

    @Override
    public String submit(TaskSubmitRequest request, Object payload) {
        return submitForResult(request, payload).getResultId();
    }

    @Override
    public TaskSubmitResult submitForResult(TaskSubmitRequest request) {
        validateRequest(request);
        LocalDateTime executeTime = LocalDateTime.now();
        return doSubmit(request, executeTime, null);
    }

    @Override
    public TaskSubmitResult submitForResult(TaskSubmitRequest request, Object payload) {
        validateRequest(request, payload);
        LocalDateTime executeTime = LocalDateTime.now();
        return doSubmit(request, executeTime, payload);
    }

    @Override
    public String submitDelay(TaskSubmitRequest request, Duration delay) {
        validateRequest(request);
        if (delay == null || delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("delay must be positive");
        }

        LocalDateTime executeTime = LocalDateTime.now().plus(delay);

        return doSubmit(request, executeTime, null).getResultId();
    }

    @Override
    public List<String> submitBatch(List<TaskSubmitRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<String> taskIds = new ArrayList<>(requests.size());
        for (TaskSubmitRequest request : requests) {
            taskIds.add(submit(request));
        }
        return taskIds;
    }

    /**
     * 执行实际的任务保存操作
     *
     * <p>构建 TaskInstance 并调用 TaskStore.save() 持久化。
     * TaskStore.save() 内部已处理 bizUniqueKey 幂等。
     */
    private TaskSubmitResult doSubmit(TaskSubmitRequest request, LocalDateTime executeTime, Object payloadOverride) {
        String serializedPayload = serializePayload(request, payloadOverride);
        String baseBizUniqueKey = buildBizUniqueKey(
                request.getTaskType(), request.getBizType(), request.getBizId());
        String strategyName = resolveStrategyName(request);
        IdempotencyStrategy strategy = resolveStrategy(strategyName);
        TaskInstance existing = taskStore.getByBizUniqueKey(baseBizUniqueKey);
        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .taskType(request.getTaskType())
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .bizUniqueKey(baseBizUniqueKey)
                .payload(serializedPayload)
                .strategyName(strategyName)
                .existingTask(existing)
                .build());

        if (decision.getAction() == IdempotencyDecision.Action.RETURN_EXISTING) {
            log.info("Task submit hit idempotency: taskType={}, bizId={}, strategy={}, traceId={}, existingTaskId={}",
                    request.getTaskType(), request.getBizId(), strategyName, TraceContext.getTraceId(),
                    decision.getExistingTaskId());
            return TaskSubmitResult.builder()
                    .taskId(decision.getExistingTaskId())
                    .fallbackId(request.getBizId())
                    .taskType(request.getTaskType())
                    .bizId(request.getBizId())
                    .bizUniqueKey(baseBizUniqueKey)
                    .created(false)
                    .existing(true)
                    .idempotencyStrategy(strategyName)
                    .reason(decision.getReason())
                    .build();
        }
        if (decision.getAction() == IdempotencyDecision.Action.REJECT) {
            throw new IllegalStateException("Task submit rejected by idempotency strategy: " + decision.getReason());
        }

        String effectiveBizUniqueKey = decision.getBizUniqueKey() != null
                ? decision.getBizUniqueKey()
                : baseBizUniqueKey;
        TaskInstance task = buildTaskInstance(request, executeTime, effectiveBizUniqueKey, serializedPayload);
        TaskInstance saved = taskStore.save(task);
        recordSubmitted(saved);
        log.info("Task submitted: id={}, type={}, bizId={}, strategy={}, traceId={}, idempotencyHit={}",
                saved.getId(), saved.getTaskType(), saved.getBizId(), strategyName, saved.getTraceId(), false);
        return TaskSubmitResult.builder()
                .taskId(saved.getId())
                .fallbackId(request.getBizId())
                .taskType(saved.getTaskType())
                .bizId(saved.getBizId())
                .bizUniqueKey(saved.getBizUniqueKey())
                .created(true)
                .existing(false)
                .idempotencyStrategy(strategyName)
                .build();
    }

    /**
     * 从 TaskSubmitRequest 构建 TaskInstance 领域对象
     */
    private TaskInstance buildTaskInstance(TaskSubmitRequest request, LocalDateTime nextExecuteTime,
                                           String bizUniqueKey, String serializedPayload) {
        return TaskInstance.builder()
                .taskType(request.getTaskType())
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .bizUniqueKey(bizUniqueKey)
                .status(TaskStatus.PENDING)
                .priority(request.getPriority())
                .payload(serializedPayload)
                .executeCount(0)
                .maxRetryCount(request.getMaxRetryCount())
                .retryStrategy(request.getRetryStrategy())
                .retryIntervalMs(request.getRetryIntervalMs())
                .nextExecuteTime(nextExecuteTime)
                .shardKey(request.getShardKey())
                .tenantId(request.getTenantId())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    private String resolveStrategyName(TaskSubmitRequest request) {
        return request.getIdempotencyStrategy() != null && !request.getIdempotencyStrategy().isBlank()
                ? request.getIdempotencyStrategy()
                : defaultIdempotencyStrategyName;
    }

    private IdempotencyStrategy resolveStrategy(String strategyName) {
        IdempotencyStrategy strategy = idempotencyStrategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown idempotency strategy: " + strategyName);
        }
        return strategy;
    }

    /**
     * 构建幂等键
     *
     * <p>格式: taskType:bizType:bizId
     * 保证同一业务动作不会被重复投递。
     */
    private String buildBizUniqueKey(String taskType, String bizType, String bizId) {
        return taskType + ":" + bizType + ":" + bizId;
    }

    private String serializePayload(TaskSubmitRequest request, Object payloadOverride) {
        Object payload = payloadOverride != null
                ? payloadOverride
                : request.getPayloadObject() != null ? request.getPayloadObject() : request.getPayload();
        String serializedPayload = payloadSerializer.serialize(payload);
        if (serializedPayload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return serializedPayload;
    }

    /**
     * 校验必填字段
     */
    private void validateRequest(TaskSubmitRequest request) {
        validateRequestMetadata(request);
        if (request.getPayload() == null && request.getPayloadObject() == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    private void validateRequest(TaskSubmitRequest request, Object payload) {
        validateRequestMetadata(request);
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    private void validateRequestMetadata(TaskSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TaskSubmitRequest must not be null");
        }
        if (request.getTaskType() == null || request.getTaskType().isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        if (request.getBizType() == null || request.getBizType().isBlank()) {
            throw new IllegalArgumentException("bizType must not be blank");
        }
        if (request.getBizId() == null || request.getBizId().isBlank()) {
            throw new IllegalArgumentException("bizId must not be blank");
        }
    }

    private void recordSubmitted(TaskInstance task) {
        try {
            metricsRecorder.record(TaskExecutionMetricsEvent.builder()
                    .taskId(task.getId())
                    .taskType(task.getTaskType())
                    .status(TaskStatus.PENDING)
                    .workerId(task.getWorkerId())
                    .durationMs(0)
                    .success(true)
                    .retryCount(0)
                    .traceId(task.getTraceId())
                    .eventTime(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to record task submitted metrics event: taskId={}, reason={}",
                    task.getId(), e.getMessage());
        }
    }
}
