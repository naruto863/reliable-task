package com.reliabletask.executor.template;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskExecutionMetricsEvent;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadCodec;
import com.reliabletask.core.spi.TaskPayloadCodecAdapters;
import com.reliabletask.core.spi.TaskPayloadCodecContext;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskCommandStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.spi.TaskTraceIdGenerator;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.trace.DefaultTaskTraceIdGenerator;
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
 *   → TaskCommandStore.save() → PENDING 状态入库
 * </pre>
 *
 * <p>这里同时承担投递侧的幂等决策、payload 编码和 traceId 生成。它们都放在入库前完成，
 * 是为了让存储层只关心任务记录本身，避免不同存储实现重复理解业务请求对象。
 */
@Slf4j
public class TransactionAwareTaskTemplate implements TaskTemplate {

    private static final int MAX_BIZ_UNIQUE_KEY_LENGTH = 256;
    private static final int MAX_TRACE_ID_LENGTH = 64;

    private final TaskCommandStore taskStore;
    private final Map<String, IdempotencyStrategy> idempotencyStrategies;
    private final String defaultIdempotencyStrategyName;
    private final TaskPayloadCodec payloadCodec;
    private final TaskMetricsRecorder metricsRecorder;
    private final TaskEventPublisher eventPublisher;
    private final TaskTraceIdGenerator traceIdGenerator;

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore) {
        this(taskStore, List.of(new StrictUniqueIdempotencyStrategy()), StrictUniqueIdempotencyStrategy.NAME,
                new JacksonTaskPayloadSerializer());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName, new JacksonTaskPayloadSerializer());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadSerializer payloadSerializer) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                toCodec(payloadSerializer), new NoopTaskMetricsRecorder());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadCodec payloadCodec) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                payloadCodec, new NoopTaskMetricsRecorder());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadSerializer payloadSerializer,
                                        TaskMetricsRecorder metricsRecorder) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                toCodec(payloadSerializer), metricsRecorder, new TaskEventPublisher());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadCodec payloadCodec,
                                        TaskMetricsRecorder metricsRecorder) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                payloadCodec, metricsRecorder, new TaskEventPublisher());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadSerializer payloadSerializer,
                                        TaskMetricsRecorder metricsRecorder,
                                        TaskEventPublisher eventPublisher) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                toCodec(payloadSerializer), metricsRecorder, eventPublisher);
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadCodec payloadCodec,
                                        TaskMetricsRecorder metricsRecorder,
                                        TaskEventPublisher eventPublisher) {
        this(taskStore, idempotencyStrategies, defaultIdempotencyStrategyName,
                payloadCodec, metricsRecorder, eventPublisher, new DefaultTaskTraceIdGenerator());
    }

    public TransactionAwareTaskTemplate(TaskCommandStore taskStore,
                                        List<IdempotencyStrategy> idempotencyStrategies,
                                        String defaultIdempotencyStrategyName,
                                        TaskPayloadCodec payloadCodec,
                                        TaskMetricsRecorder metricsRecorder,
                                        TaskEventPublisher eventPublisher,
                                        TaskTraceIdGenerator traceIdGenerator) {
        this.taskStore = taskStore;
        this.idempotencyStrategies = new HashMap<>();
        if (idempotencyStrategies != null) {
            for (IdempotencyStrategy strategy : idempotencyStrategies) {
                this.idempotencyStrategies.put(strategy.getName(), strategy);
            }
        }
        this.defaultIdempotencyStrategyName = defaultIdempotencyStrategyName;
        this.payloadCodec = payloadCodec != null ? payloadCodec : defaultCodec();
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new NoopTaskMetricsRecorder();
        this.eventPublisher = eventPublisher != null ? eventPublisher : new TaskEventPublisher();
        this.traceIdGenerator = traceIdGenerator != null ? traceIdGenerator : new DefaultTaskTraceIdGenerator();
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
     * <p>构建 TaskInstance 并调用 TaskCommandStore.save() 持久化。
     * TaskCommandStore.save() 内部已处理 bizUniqueKey 幂等。
     *
     * <p>幂等策略先基于当前库内最新任务做一次业务决策，然后存储层再用唯一键/重复键兜底。
     * 这两层保护分别覆盖“可配置业务策略”和“并发投递竞态”，不要只保留其中一层。
     */
    private TaskSubmitResult doSubmit(TaskSubmitRequest request, LocalDateTime executeTime, Object payloadOverride) {
        String traceId = resolveTraceId(request);
        String serializedPayload = serializePayload(request, payloadOverride, traceId);
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        String baseBizUniqueKey = resolveBizUniqueKey(request, idempotencyKey);
        String strategyName = resolveStrategyName(request);
        IdempotencyStrategy strategy = resolveStrategy(strategyName);
        TaskInstance existing = taskStore.getByBizUniqueKey(baseBizUniqueKey);
        IdempotencyDecision decision = strategy.decide(IdempotencyContext.builder()
                .taskType(request.getTaskType())
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .bizUniqueKey(baseBizUniqueKey)
                .idempotencyKey(idempotencyKey)
                .payload(serializedPayload)
                .strategyName(strategyName)
                .existingTask(existing)
                .build());

        if (decision.getAction() == IdempotencyDecision.Action.RETURN_EXISTING) {
            log.info("Task submit hit idempotency: taskType={}, bizId={}, strategy={}, traceId={}, existingTaskId={}",
                    request.getTaskType(), request.getBizId(), strategyName, traceId,
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

        // 策略可以改写最终入库幂等键，例如允许终态后重新投递时生成新的 bizUniqueKey。
        String effectiveBizUniqueKey = decision.getBizUniqueKey() != null
                ? decision.getBizUniqueKey()
                : baseBizUniqueKey;
        validateBizUniqueKeyLength(effectiveBizUniqueKey);
        TaskInstance task = buildTaskInstance(request, executeTime, effectiveBizUniqueKey, serializedPayload, traceId);
        TaskInstance saved = taskStore.save(task);
        recordSubmitted(saved);
        eventPublisher.publish(TaskEvent.of(TaskEventType.SUBMITTED, saved,
                null, TaskStatus.PENDING, "task submitted"));
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
                                           String bizUniqueKey, String serializedPayload, String traceId) {
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
                .traceId(traceId)
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

    private String resolveBizUniqueKey(TaskSubmitRequest request, String idempotencyKey) {
        String bizUniqueKey = idempotencyKey != null
                ? idempotencyKey
                : buildBizUniqueKey(request.getTaskType(), request.getBizType(), request.getBizId());
        validateBizUniqueKeyLength(bizUniqueKey);
        return bizUniqueKey;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank when provided");
        }
        return normalized;
    }

    private void validateBizUniqueKeyLength(String bizUniqueKey) {
        if (bizUniqueKey != null && bizUniqueKey.length() > MAX_BIZ_UNIQUE_KEY_LENGTH) {
            throw new IllegalArgumentException("bizUniqueKey/idempotencyKey length must not exceed "
                    + MAX_BIZ_UNIQUE_KEY_LENGTH);
        }
    }

    /**
     * 统一完成投递 payload 入库编码。
     *
     * <p>优先级为显式 payloadOverride、request.payloadObject、request.payload。这样旧版字符串 payload
     * 和新版对象 payload 可以共存，同时给 TaskPayloadCodec 足够上下文做加密、压缩或类型化 JSON 等扩展。
     */
    private String serializePayload(TaskSubmitRequest request, Object payloadOverride, String traceId) {
        Object payload = payloadOverride != null
                ? payloadOverride
                : request.getPayloadObject() != null ? request.getPayloadObject() : request.getPayload();
        TaskPayloadCodecContext context = TaskPayloadCodecContext.builder()
                .operation(TaskPayloadCodecContext.Operation.ENCODE)
                .taskType(request.getTaskType())
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .tenantId(request.getTenantId())
                .shardKey(request.getShardKey())
                .traceId(traceId)
                .targetType(payload == null ? null : payload.getClass())
                .build();
        String serializedPayload = payloadCodec.encode(payload, context);
        if (serializedPayload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return serializedPayload;
    }

    /**
     * 生成并约束 traceId。
     *
     * <p>自定义生成器返回空值时回退默认实现，避免投递链路因为观测扩展实现不严谨而丢失 traceId。
     */
    private String resolveTraceId(TaskSubmitRequest request) {
        String traceId = traceIdGenerator.generate(request);
        if (traceId == null || traceId.isBlank()) {
            traceId = new DefaultTaskTraceIdGenerator().generate(request);
        }
        if (traceId.length() > MAX_TRACE_ID_LENGTH) {
            throw new IllegalArgumentException("traceId length must not exceed " + MAX_TRACE_ID_LENGTH);
        }
        return traceId;
    }

    private static TaskPayloadCodec toCodec(TaskPayloadSerializer payloadSerializer) {
        return TaskPayloadCodecAdapters.fromSerializer(
                payloadSerializer != null ? payloadSerializer : new JacksonTaskPayloadSerializer());
    }

    private static TaskPayloadCodec defaultCodec() {
        return TaskPayloadCodecAdapters.fromSerializer(new JacksonTaskPayloadSerializer());
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
