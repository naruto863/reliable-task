package com.reliabletask.executor.template;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskCommandStore;
import com.reliabletask.core.spi.TaskPayloadCodec;
import com.reliabletask.core.spi.TaskPayloadCodecContext;
import com.reliabletask.core.spi.TaskTraceIdGenerator;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TransactionAwareTaskTemplate 测试")
@ExtendWith(MockitoExtension.class)
class TransactionAwareTaskTemplateTest {

    @Mock
    private TaskCommandStore taskStore;

    private TransactionAwareTaskTemplate template;

    private TaskSubmitRequest validRequest;

    @BeforeEach
    void setUp() {
        template = new TransactionAwareTaskTemplate(taskStore);

        validRequest = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-001")
                .payload("{\"orderId\":\"ORD-001\"}")
                .priority(3)
                .maxRetryCount(5)
                .retryStrategy(RetryStrategyType.EXPONENTIAL)
                .retryIntervalMs(2000L)
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    // ==================== submit without transaction ====================

    @Test
    @DisplayName("submit - 无事务时立即调用 save")
    void submit_noTransaction_savesImmediately() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            return TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build();
        });

        String result = template.submit(validRequest);

        assertEquals("1", result);
        verify(taskStore).save(any());
    }

    @Test
    @DisplayName("submitForResult - 新任务返回 created")
    void submitForResult_newTask_returnsCreatedResult() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(1L);
            return task;
        });

        TaskSubmitResult result = template.submitForResult(validRequest);

        assertEquals("1", result.getResultId());
        assertTrue(result.isCreated());
        assertFalse(result.isExisting());
        assertEquals(StrictUniqueIdempotencyStrategy.NAME, result.getIdempotencyStrategy());
    }

    @Test
    @DisplayName("submitForResult - 无 TraceContext 时自动生成 rt 前缀 traceId")
    void submitForResult_withoutTraceContext_generatesRtTraceId() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(2L);
            return task;
        });

        template.submitForResult(validRequest);

        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertTrue(captor.getValue().getTraceId().startsWith("rt-"));
        assertTrue(captor.getValue().getTraceId().length() <= 64);
    }

    @Test
    @DisplayName("submitForResult - 有 TraceContext 时复用当前 traceId")
    void submitForResult_withTraceContext_reusesCurrentTraceId() {
        TraceContext.setTraceId("trace-current");
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(3L);
            return task;
        });

        template.submitForResult(validRequest);

        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("trace-current", captor.getValue().getTraceId());
    }

    @Test
    @DisplayName("submitForResult - 自定义 traceId generator 可覆盖默认策略")
    void submitForResult_customTraceIdGenerator_overridesDefaultStrategy() {
        TaskPayloadCodec codec = new TaskPayloadCodec() {
            @Override
            public String encode(Object payload, TaskPayloadCodecContext context) {
                return String.valueOf(payload);
            }

            @Override
            public <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context) {
                return targetType.cast(payload);
            }
        };
        TaskTraceIdGenerator generator = request -> "custom-" + request.getBizId();
        template = new TransactionAwareTaskTemplate(taskStore, List.of(new StrictUniqueIdempotencyStrategy()),
                StrictUniqueIdempotencyStrategy.NAME, codec, null, null, generator);
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(4L);
            return task;
        });

        template.submitForResult(validRequest);

        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("custom-ORD-001", captor.getValue().getTraceId());
    }

    @Test
    @DisplayName("submitForResult - 新任务发布 SUBMITTED 事件")
    void submitForResult_newTask_publishesSubmittedEvent() {
        List<TaskEvent> events = new ArrayList<>();
        template = new TransactionAwareTaskTemplate(taskStore, List.of(new StrictUniqueIdempotencyStrategy()),
                StrictUniqueIdempotencyStrategy.NAME, new com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer(),
                null, new TaskEventPublisher(List.of(events::add)));
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(1L);
            return task;
        });

        template.submitForResult(validRequest);

        assertEquals(1, events.size());
        TaskEvent event = events.get(0);
        assertEquals(TaskEventType.SUBMITTED, event.getEventType());
        assertEquals(1L, event.getTaskId());
        assertNull(event.getStatusBefore());
        assertEquals(TaskStatus.PENDING, event.getStatusAfter());
        assertEquals("CREATE_SHIPMENT", event.getTaskType());
        assertEquals("ORD-001", event.getBizId());
    }

    @Test
    @DisplayName("submitForResult - STRICT_UNIQUE 命中已有任务不重复保存")
    void submitForResult_strictUniqueHit_returnsExisting() {
        when(taskStore.getByBizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001"))
                .thenReturn(TaskInstance.builder().id(99L).status(TaskStatus.PENDING).build());

        TaskSubmitResult result = template.submitForResult(validRequest);

        assertEquals("99", result.getResultId());
        assertFalse(result.isCreated());
        assertTrue(result.isExisting());
        verify(taskStore, never()).save(any());
    }

    @Test
    @DisplayName("submitForResult - 显式 idempotencyKey 作为入库幂等键")
    void submitForResult_explicitIdempotencyKey_usesExplicitKey() {
        validRequest.setIdempotencyKey("shipment:order:ORD-001");
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });

        TaskSubmitResult result = template.submitForResult(validRequest);

        assertTrue(result.isCreated());
        assertEquals("shipment:order:ORD-001", result.getBizUniqueKey());
        verify(taskStore).getByBizUniqueKey("shipment:order:ORD-001");
        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("shipment:order:ORD-001", captor.getValue().getBizUniqueKey());
    }

    @Test
    @DisplayName("submitForResult - 显式 idempotencyKey 命中已有任务")
    void submitForResult_explicitIdempotencyKeyHit_returnsExisting() {
        validRequest.setIdempotencyKey("shipment:order:ORD-001");
        when(taskStore.getByBizUniqueKey("shipment:order:ORD-001"))
                .thenReturn(TaskInstance.builder().id(88L).status(TaskStatus.PENDING).build());

        TaskSubmitResult result = template.submitForResult(validRequest);

        assertEquals("88", result.getResultId());
        assertFalse(result.isCreated());
        assertTrue(result.isExisting());
        assertEquals("shipment:order:ORD-001", result.getBizUniqueKey());
        verify(taskStore, never()).save(any());
    }

    @Test
    @DisplayName("submitForResult - ALLOW_AFTER_TERMINAL 终态后创建新任务")
    void submitForResult_allowAfterTerminal_createsNewTask() {
        List<IdempotencyStrategy> strategies = List.of(
                new StrictUniqueIdempotencyStrategy(),
                new AllowAfterTerminalIdempotencyStrategy());
        template = new TransactionAwareTaskTemplate(taskStore, strategies, StrictUniqueIdempotencyStrategy.NAME);
        validRequest.setIdempotencyStrategy(AllowAfterTerminalIdempotencyStrategy.NAME);
        when(taskStore.getByBizUniqueKey("CREATE_SHIPMENT:ORDER:ORD-001"))
                .thenReturn(TaskInstance.builder().id(99L).status(TaskStatus.SUCCESS).build());
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(100L);
            return task;
        });

        TaskSubmitResult result = template.submitForResult(validRequest);

        assertEquals("100", result.getResultId());
        assertTrue(result.isCreated());
        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertTrue(captor.getValue().getBizUniqueKey()
                .startsWith("CREATE_SHIPMENT:ORDER:ORD-001:RERUN:99:"));
    }

    @Test
    @DisplayName("submitForResult - 策略改写后的过长 bizUniqueKey 抛异常")
    void submitForResult_strategyGeneratedTooLongBizUniqueKey_throwsException() {
        List<IdempotencyStrategy> strategies = List.of(
                new StrictUniqueIdempotencyStrategy(),
                new AllowAfterTerminalIdempotencyStrategy());
        template = new TransactionAwareTaskTemplate(taskStore, strategies, StrictUniqueIdempotencyStrategy.NAME);
        String idempotencyKey = "x".repeat(250);
        validRequest.setIdempotencyKey(idempotencyKey);
        validRequest.setIdempotencyStrategy(AllowAfterTerminalIdempotencyStrategy.NAME);
        when(taskStore.getByBizUniqueKey(idempotencyKey))
                .thenReturn(TaskInstance.builder().id(99L).status(TaskStatus.SUCCESS).build());

        assertThrows(IllegalArgumentException.class, () -> template.submitForResult(validRequest));
        verify(taskStore, never()).save(any());
    }

    @Test
    @DisplayName("submit - 无事务时构建正确的 TaskInstance")
    void submit_noTransaction_buildsCorrectTaskInstance() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            return TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build();
        });

        template.submit(validRequest);

        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        TaskInstance saved = captor.getValue();

        assertEquals("CREATE_SHIPMENT", saved.getTaskType());
        assertEquals("ORDER", saved.getBizType());
        assertEquals("ORD-001", saved.getBizId());
        assertEquals("CREATE_SHIPMENT:ORDER:ORD-001", saved.getBizUniqueKey());
        assertEquals(TaskStatus.PENDING, saved.getStatus());
        assertEquals(3, saved.getPriority());
        assertEquals(5, saved.getMaxRetryCount());
        assertEquals(RetryStrategyType.EXPONENTIAL, saved.getRetryStrategy());
        assertEquals(2000L, saved.getRetryIntervalMs());
        assertEquals(0, saved.getExecuteCount());
        assertNotNull(saved.getNextExecuteTime());
    }

    @Test
    @DisplayName("submit - request 对象 payload 序列化为 JSON 入库")
    void submit_requestObjectPayload_serializesToJson() {
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-JSON-001")
                .payloadObject(new ShipmentPayload("ORD-JSON-001", 3))
                .build();
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });

        String result = template.submit(request);

        assertEquals("10", result);
        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("{\"orderId\":\"ORD-JSON-001\",\"quantity\":3}", captor.getValue().getPayload());
    }

    @Test
    @DisplayName("submit - 显式对象 payload 序列化为 JSON 入库")
    void submit_explicitObjectPayload_serializesToJson() {
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-JSON-002")
                .build();
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(11L);
            return task;
        });

        String result = template.submit(request, new ShipmentPayload("ORD-JSON-002", 5));

        assertEquals("11", result);
        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("{\"orderId\":\"ORD-JSON-002\",\"quantity\":5}", captor.getValue().getPayload());
    }

    @Test
    @DisplayName("submit - 自定义 codec 接收入库上下文")
    void submit_customPayloadCodec_receivesSubmissionContext() {
        AtomicReference<TaskPayloadCodecContext> contextRef = new AtomicReference<>();
        TaskPayloadCodec codec = new TaskPayloadCodec() {
            @Override
            public String encode(Object payload, TaskPayloadCodecContext context) {
                contextRef.set(context);
                return "codec:" + context.getTaskType() + ":" + payload;
            }

            @Override
            public <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context) {
                throw new UnsupportedOperationException("decode is not used by submit");
            }
        };
        template = new TransactionAwareTaskTemplate(taskStore, List.of(new StrictUniqueIdempotencyStrategy()),
                StrictUniqueIdempotencyStrategy.NAME, codec);
        TaskSubmitRequest request = TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId("ORD-CODEC-001")
                .tenantId("tenant-a")
                .shardKey("shard-1")
                .payloadObject(new ShipmentPayload("ORD-CODEC-001", 7))
                .build();
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            task.setId(12L);
            return task;
        });

        String result = template.submit(request);

        assertEquals("12", result);
        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        assertEquals("codec:CREATE_SHIPMENT:ShipmentPayload[orderId=ORD-CODEC-001, quantity=7]",
                captor.getValue().getPayload());
        assertEquals(TaskPayloadCodecContext.Operation.ENCODE, contextRef.get().getOperation());
        assertEquals("CREATE_SHIPMENT", contextRef.get().getTaskType());
        assertEquals("ORDER", contextRef.get().getBizType());
        assertEquals("ORD-CODEC-001", contextRef.get().getBizId());
        assertEquals("tenant-a", contextRef.get().getTenantId());
        assertEquals("shard-1", contextRef.get().getShardKey());
        assertEquals(ShipmentPayload.class, contextRef.get().getTargetType());
    }

    // ==================== submit with transaction ====================

    @Test
    @DisplayName("submit - 有事务同步时也立即 save")
    void submit_withTransaction_savesImmediately() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            return TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build();
        });

        TransactionSynchronizationManager.initSynchronization();

        try {
            String result = template.submit(validRequest);

            assertEquals("1", result);
            verify(taskStore).save(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== submitDelay ====================

    @Test
    @DisplayName("submitDelay - 无事务时以未来时间保存")
    void submitDelay_noTransaction_savesWithFutureTime() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            return TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build();
        });

        template.submitDelay(validRequest, Duration.ofMinutes(10));

        ArgumentCaptor<TaskInstance> captor = ArgumentCaptor.forClass(TaskInstance.class);
        verify(taskStore).save(captor.capture());
        TaskInstance saved = captor.getValue();

        assertTrue(saved.getNextExecuteTime().isAfter(LocalDateTime.now().plusMinutes(9)));
    }

    @Test
    @DisplayName("submitDelay - 有事务同步时也立即 save")
    void submitDelay_withTransaction_savesImmediately() {
        when(taskStore.save(any())).thenAnswer(invocation -> {
            TaskInstance task = invocation.getArgument(0);
            return TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build();
        });

        TransactionSynchronizationManager.initSynchronization();

        try {
            template.submitDelay(validRequest, Duration.ofMinutes(5));
            verify(taskStore).save(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("submitDelay - 负数延迟抛异常")
    void submitDelay_negativeDelay_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> template.submitDelay(validRequest, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("submitDelay - 零延迟抛异常")
    void submitDelay_zeroDelay_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> template.submitDelay(validRequest, Duration.ZERO));
    }

    // ==================== submitBatch ====================

    @Test
    @DisplayName("submitBatch - 批量提交返回任务 ID 列表")
    void submitBatch_returnsTaskIdList() {
        when(taskStore.save(any()))
                .thenReturn(TaskInstance.builder().id(1L).status(TaskStatus.PENDING).build())
                .thenReturn(TaskInstance.builder().id(2L).status(TaskStatus.PENDING).build());

        List<TaskSubmitRequest> requests = List.of(
                TaskSubmitRequest.builder()
                        .taskType("TYPE_A").bizType("BIZ").bizId("ID-1")
                        .payload("{}").build(),
                TaskSubmitRequest.builder()
                        .taskType("TYPE_B").bizType("BIZ").bizId("ID-2")
                        .payload("{}").build()
        );

        List<String> result = template.submitBatch(requests);

        assertEquals(2, result.size());
        assertEquals("1", result.get(0));
        assertEquals("2", result.get(1));
        verify(taskStore, times(2)).save(any());
    }

    @Test
    @DisplayName("submitBatch - 空列表返回空列表")
    void submitBatch_emptyList_returnsEmptyList() {
        assertTrue(template.submitBatch(List.of()).isEmpty());
    }

    @Test
    @DisplayName("submitBatch - null 返回空列表")
    void submitBatch_null_returnsEmptyList() {
        assertTrue(template.submitBatch(null).isEmpty());
    }

    // ==================== Validation ====================

    @Test
    @DisplayName("submit - null 请求抛异常")
    void submit_nullRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> template.submit(null));
    }

    @Test
    @DisplayName("submit - 空 taskType 抛异常")
    void submit_blankTaskType_throwsException() {
        validRequest.setTaskType("");
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    @Test
    @DisplayName("submit - 空 bizType 抛异常")
    void submit_blankBizType_throwsException() {
        validRequest.setBizType(null);
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    @Test
    @DisplayName("submit - 空 bizId 抛异常")
    void submit_blankBizId_throwsException() {
        validRequest.setBizId("   ");
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    @Test
    @DisplayName("submit - null payload 抛异常")
    void submit_nullPayload_throwsException() {
        validRequest.setPayload(null);
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    @Test
    @DisplayName("submit - 空白 idempotencyKey 抛异常")
    void submit_blankIdempotencyKey_throwsException() {
        validRequest.setIdempotencyKey("   ");
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    @Test
    @DisplayName("submit - 过长 idempotencyKey 抛异常")
    void submit_tooLongIdempotencyKey_throwsException() {
        validRequest.setIdempotencyKey("x".repeat(257));
        assertThrows(IllegalArgumentException.class, () -> template.submit(validRequest));
    }

    record ShipmentPayload(String orderId, int quantity) {
    }
}
