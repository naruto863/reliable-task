package com.reliabletask.core.spi;

import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.WorkerHeartbeat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务运维存储 SPI。
 *
 * <p>覆盖人工操作、审计、批量操作和 Worker 心跳等运维写入或管理能力。
 * Admin 写接口和 Worker heartbeat 路径应优先依赖本接口。
 */
public interface TaskOperationsStore {

    /**
     * 取消任务。
     *
     * @param id 任务 ID
     * @return 是否取消成功
     */
    boolean cancelTask(Long id);

    /**
     * 手动重新入队。
     *
     * @param id 任务 ID
     * @return 是否重新入队成功
     */
    boolean requeueTask(Long id);

    /**
     * 更新任务 payload。
     *
     * @param id      任务 ID
     * @param payload 新的 payload JSON 字符串
     * @return 是否更新成功
     */
    boolean updatePayload(Long id, String payload);

    /**
     * 上报 Worker 心跳。
     *
     * @param heartbeat Worker 心跳
     */
    default void reportWorkerHeartbeat(WorkerHeartbeat heartbeat) {
        // no-op
    }

    /**
     * 查询 Worker 列表。
     *
     * @return Worker 心跳列表
     */
    default List<WorkerHeartbeat> listWorkers() {
        return List.of();
    }

    /**
     * 查询失联 Worker。
     *
     * @param heartbeatBefore 最晚心跳时间
     * @return 失联 Worker 列表
     */
    default List<WorkerHeartbeat> findStaleWorkers(LocalDateTime heartbeatBefore) {
        return List.of();
    }

    /**
     * 保存审计日志。
     *
     * @param auditLog 审计日志
     */
    default void saveAuditLog(AuditLog auditLog) {
        // no-op
    }

    /**
     * 按任务 ID 查询审计日志。
     *
     * @param taskId 任务 ID
     * @return 审计日志列表
     */
    default List<AuditLog> getAuditLogsByTaskId(Long taskId) {
        return List.of();
    }

    /**
     * 分页查询审计日志。
     *
     * @param operator        操作人，可为空
     * @param createTimeStart 创建时间开始，可为空
     * @param createTimeEnd   创建时间结束，可为空
     * @param pageNum         页码
     * @param pageSize        每页数量
     * @return 分页审计日志
     */
    default PageResult<AuditLog> listAuditLogs(String operator,
                                               LocalDateTime createTimeStart,
                                               LocalDateTime createTimeEnd,
                                               int pageNum,
                                               int pageSize) {
        return PageResult.of(List.of(), 0, pageNum, pageSize);
    }

    /**
     * 创建批量操作记录。
     *
     * @param operationType    操作类型
     * @param operator         操作人
     * @param taskType         任务类型，可为空
     * @param taskStatus       任务状态，可为空
     * @param createTimeStart  创建时间开始，可为空
     * @param createTimeEnd    创建时间结束，可为空
     * @param limit            最大操作数量
     * @param dryRun           是否试运行
     * @param requestCondition 请求条件摘要
     * @param traceId          链路追踪 ID
     * @return 批量操作 ID
     */
    default Long createBatchOperation(String operationType, String operator, String taskType,
                                      TaskStatus taskStatus, LocalDateTime createTimeStart,
                                      LocalDateTime createTimeEnd, int limit, boolean dryRun,
                                      String requestCondition, String traceId) {
        throw new UnsupportedOperationException("Batch operation is not supported");
    }

    /**
     * 更新批量操作结果。
     *
     * @param result 批量操作结果
     * @return 是否更新成功
     */
    default boolean updateBatchOperationResult(BatchOperationResult result) {
        throw new UnsupportedOperationException("Batch operation is not supported");
    }

    /**
     * 查询批量操作结果。
     *
     * @param batchOperationId 批量操作 ID
     * @return 批量操作结果，不存在时返回 null
     */
    default BatchOperationResult getBatchOperationResult(Long batchOperationId) {
        return null;
    }

    /**
     * 按条件查询可操作任务 ID。
     *
     * @param taskType        任务类型，可为空
     * @param status          任务状态，可为空
     * @param createTimeStart 创建时间开始，可为空
     * @param createTimeEnd   创建时间结束，可为空
     * @param limit           最大返回数量
     * @return 任务 ID 列表
     */
    default List<Long> findOperableTaskIds(String taskType, TaskStatus status,
                                           LocalDateTime createTimeStart,
                                           LocalDateTime createTimeEnd,
                                           int limit) {
        return List.of();
    }
}
