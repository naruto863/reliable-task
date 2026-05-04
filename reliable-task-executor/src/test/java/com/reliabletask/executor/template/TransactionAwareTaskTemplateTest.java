package com.reliabletask.executor.template;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TransactionAwareTaskTemplate 测试")
@ExtendWith(MockitoExtension.class)
class TransactionAwareTaskTemplateTest {

    @Mock
    private TaskStore taskStore;

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

    record ShipmentPayload(String orderId, int quantity) {
    }
}
