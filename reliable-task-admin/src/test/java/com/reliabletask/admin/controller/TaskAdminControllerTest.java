package com.reliabletask.admin.controller;

import com.reliabletask.admin.model.Result;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskAdminController 测试")
@ExtendWith(MockitoExtension.class)
class TaskAdminControllerTest {

    @Mock
    private TaskStore taskStore;

    private TaskAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new TaskAdminController(taskStore);
    }

    @Test
    @DisplayName("listTasks - 正确映射查询参数并返回分页结果")
    void listTasks_mapsQueryRequestAndReturnsResult() {
        PageResult<TaskVO> expected = PageResult.of(List.of(), 0, 2, 10);
        when(taskStore.listTasks(any())).thenReturn(expected);

        Result<PageResult<TaskVO>> result = controller.listTasks(
                2, 10, TaskStatus.RETRYING.getCode(),
                "CREATE_SHIPMENT", "ORDER", "ORD-1", "worker-a",
                "trace-1", "tenant-1",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-31T23:59:59"),
                "viewer"
        );

        ArgumentCaptor<TaskQueryRequest> captor = ArgumentCaptor.forClass(TaskQueryRequest.class);
        verify(taskStore).listTasks(captor.capture());

        TaskQueryRequest captured = captor.getValue();
        assertEquals(2, captured.getPageNum());
        assertEquals(10, captured.getPageSize());
        assertEquals(TaskStatus.RETRYING, captured.getStatus());
        assertEquals("CREATE_SHIPMENT", captured.getTaskType());
        assertEquals("ORDER", captured.getBizType());
        assertEquals("ORD-1", captured.getBizId());

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertSame(expected, result.getData());
    }

    @Test
    @DisplayName("getTaskDetail - 不存在返回 404")
    void getTaskDetail_notFound_returns404() {
        when(taskStore.getTaskDetail(99L)).thenReturn(null);

        Result<TaskDetailVO> result = controller.getTaskDetail(99L, "viewer");

        assertEquals(404, result.getCode());
        assertTrue(result.getMessage().contains("Task not found: 99"));
        assertNull(result.getData());
    }

    @Test
    @DisplayName("retry - 不可重试任务返回 400")
    void retry_notAllowed_returns400() {
        when(taskStore.requeueTask(1L)).thenReturn(false);

        Result<Boolean> result = controller.retry(1L, "admin", "trace-1");

        assertEquals(400, result.getCode());
        assertFalse(Boolean.TRUE.equals(result.getData()));
        verify(taskStore).saveAuditLog(any(AuditLog.class));
    }

    @Test
    @DisplayName("cancel - 可取消任务返回 success")
    void cancel_allowed_returnsSuccess() {
        when(taskStore.cancelTask(1L)).thenReturn(true);

        Result<Boolean> result = controller.cancel(1L, "admin", "trace-1");

        assertEquals(200, result.getCode());
        assertEquals(Boolean.TRUE, result.getData());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(taskStore).saveAuditLog(captor.capture());
        assertEquals("TASK_CANCEL", captor.getValue().getOperationType());
        assertEquals("admin", captor.getValue().getOperator());
        assertEquals("SUCCESS", captor.getValue().getResult());
    }

    @Test
    @DisplayName("updatePayload - 缺失 payload 返回 400")
    void updatePayload_missingPayload_returns400() {
        Result<Boolean> result = controller.updatePayload(1L, Map.of("k", "v"), "admin", "trace-1");

        assertEquals(400, result.getCode());
        assertEquals("payload is required", result.getMessage());
        assertNull(result.getData());
        verify(taskStore).saveAuditLog(any(AuditLog.class));
    }

    @Test
    @DisplayName("updatePayload - store 返回 false 时返回 400")
    void updatePayload_storeRejects_returns400() {
        when(taskStore.updatePayload(2L, "{\"a\":1}")).thenReturn(false);

        Result<Boolean> result = controller.updatePayload(2L, Map.of("payload", "{\"a\":1}"), "admin", "trace-1");

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("only be updated"));
    }

    @Test
    @DisplayName("requeue - 成功时写入审计")
    void requeue_allowed_recordsAudit() {
        when(taskStore.requeueTask(3L)).thenReturn(true);

        Result<Boolean> result = controller.requeue(3L, "ops", "trace-3");

        assertEquals(200, result.getCode());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(taskStore).saveAuditLog(captor.capture());
        assertEquals("TASK_REQUEUE", captor.getValue().getOperationType());
        assertEquals("ops", captor.getValue().getOperator());
        assertEquals(3L, captor.getValue().getTaskId());
        assertEquals("trace-3", captor.getValue().getTraceId());
    }

    @Test
    @DisplayName("getAuditLogsByTaskId - 返回任务审计日志")
    void getAuditLogsByTaskId_returnsLogs() {
        List<AuditLog> logs = List.of(AuditLog.builder().taskId(1L).operationType("TASK_CANCEL").build());
        when(taskStore.getAuditLogsByTaskId(1L)).thenReturn(logs);

        Result<List<AuditLog>> result = controller.getAuditLogsByTaskId(1L, "auditor");

        assertEquals(200, result.getCode());
        assertSame(logs, result.getData());
    }

    @Test
    @DisplayName("listWorkers - 返回 Worker 心跳列表")
    void listWorkers_returnsWorkers() {
        List<WorkerHeartbeat> workers = List.of(WorkerHeartbeat.builder()
                .workerId("worker-1")
                .appName("demo")
                .status("ONLINE")
                .runningTaskCount(1)
                .maxConcurrency(4)
                .availableCapacity(3)
                .build());
        when(taskStore.listWorkers()).thenReturn(workers);

        Result<List<WorkerHeartbeat>> result = controller.listWorkers("viewer");

        assertEquals(200, result.getCode());
        assertSame(workers, result.getData());
    }

    @Test
    @DisplayName("listStaleWorkers - 按配置阈值查询失联 Worker")
    void listStaleWorkers_usesThreshold() {
        controller = new TaskAdminController(taskStore, false, null, 30L);
        List<WorkerHeartbeat> workers = List.of(WorkerHeartbeat.builder().workerId("worker-stale").build());
        when(taskStore.findStaleWorkers(any(LocalDateTime.class))).thenReturn(workers);

        Result<List<WorkerHeartbeat>> result = controller.listStaleWorkers("viewer");

        assertEquals(200, result.getCode());
        assertSame(workers, result.getData());
        verify(taskStore).findStaleWorkers(argThat(time ->
                java.time.Duration.between(time, LocalDateTime.now()).toSeconds() >= 29));
    }

    @Test
    @DisplayName("auth - Worker 查询复用 TASK_VIEW 权限")
    void authProviderRejectsWorkerView_returnsForbidden() {
        TaskAuthorizationProvider provider = (operator, action, taskId) -> false;
        controller = new TaskAdminController(taskStore, true, provider);

        Result<List<WorkerHeartbeat>> result = controller.listWorkers("viewer");

        assertEquals(403, result.getCode());
        verify(taskStore).saveAuditLog(any(AuditLog.class));
    }

    @Test
    @DisplayName("auth - 开启但无 provider 时拒绝写操作并写审计")
    void authEnabledWithoutProvider_rejectsWriteOperation() {
        controller = new TaskAdminController(taskStore, true, null);

        Result<Boolean> result = controller.cancel(1L, "admin", "trace-auth");

        assertEquals(403, result.getCode());
        assertTrue(result.getMessage().contains("Forbidden"));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(taskStore).saveAuditLog(captor.capture());
        assertEquals("AUTH_DENIED", captor.getValue().getOperationType());
    }

    @Test
    @DisplayName("auth - provider 允许时执行写操作")
    void authProviderAllows_executesWriteOperation() {
        TaskAuthorizationProvider provider = (operator, action, taskId) -> true;
        controller = new TaskAdminController(taskStore, true, provider);
        when(taskStore.cancelTask(1L)).thenReturn(true);

        Result<Boolean> result = controller.cancel(1L, "admin", "trace-auth");

        assertEquals(200, result.getCode());
        verify(taskStore).cancelTask(1L);
    }

    @Test
    @DisplayName("auth - provider 拒绝查询操作")
    void authProviderRejectsView_returnsForbidden() {
        TaskAuthorizationProvider provider = (operator, action, taskId) -> false;
        controller = new TaskAdminController(taskStore, true, provider);

        Result<TaskDetailVO> result = controller.getTaskDetail(1L, "viewer");

        assertEquals(403, result.getCode());
        verify(taskStore).saveAuditLog(any(AuditLog.class));
    }

    @Test
    @DisplayName("batchRequeue - 批量重新入队 DEAD 任务并记录结果")
    void batchRequeue_requeuesDeadTasksAndRecordsResult() {
        when(taskStore.findOperableTaskIds("TYPE_A", TaskStatus.DEAD, null, null, 10))
                .thenReturn(List.of(1L, 2L));
        when(taskStore.createBatchOperation(any(), any(), any(), any(), any(), any(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(100L);
        when(taskStore.requeueTask(1L)).thenReturn(true);
        when(taskStore.requeueTask(2L)).thenReturn(false);

        Result<BatchOperationResult> result = controller.batchRequeue(
                new TaskAdminController.BatchOperationRequest("TYPE_A", null, null, null, 10, false),
                "ops", "trace-batch");

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(1, result.getData().getSuccessCount());
        assertEquals(1, result.getData().getFailCount());
        verify(taskStore).updateBatchOperationResult(any(BatchOperationResult.class));
        verify(taskStore).saveAuditLog(any(AuditLog.class));
    }

    @Test
    @DisplayName("batchCancel - 默认取消 PENDING 和 RETRYING 任务")
    void batchCancel_withoutStatus_cancelsPendingAndRetryingTasks() {
        when(taskStore.findOperableTaskIds("TYPE_A", TaskStatus.PENDING, null, null, 10))
                .thenReturn(List.of(1L));
        when(taskStore.findOperableTaskIds("TYPE_A", TaskStatus.RETRYING, null, null, 9))
                .thenReturn(List.of(2L));
        when(taskStore.createBatchOperation(any(), any(), any(), any(), any(), any(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(101L);
        when(taskStore.cancelTask(1L)).thenReturn(true);
        when(taskStore.cancelTask(2L)).thenReturn(true);

        Result<BatchOperationResult> result = controller.batchCancel(
                new TaskAdminController.BatchOperationRequest("TYPE_A", null, null, null, 10, false),
                "ops", "trace-batch");

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().getSuccessCount());
        verify(taskStore).updateBatchOperationResult(any(BatchOperationResult.class));
    }

    @Test
    @DisplayName("previewBatch - 只预览不执行任务操作")
    void previewBatch_returnsDryRunResult() {
        when(taskStore.findOperableTaskIds("TYPE_A", TaskStatus.DEAD, null, null, 5))
                .thenReturn(List.of(1L, 2L, 3L));
        when(taskStore.createBatchOperation(any(), any(), any(), any(), any(), any(), anyInt(), eq(true), any(), any()))
                .thenReturn(102L);

        Result<BatchOperationResult> result = controller.previewBatch(
                new TaskAdminController.BatchOperationRequest("TYPE_A", TaskStatus.DEAD.getCode(), null, null, 5, true),
                "ops", "trace-batch");

        assertEquals(200, result.getCode());
        assertTrue(result.getData().isDryRun());
        assertEquals(3, result.getData().getTotalCount());
        verify(taskStore, org.mockito.Mockito.never()).requeueTask(any());
        verify(taskStore, org.mockito.Mockito.never()).cancelTask(any());
    }
}
