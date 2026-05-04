package com.reliabletask.core.spi;

import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.model.BatchOperationResult;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存储层 SPI
 *
 * <p>抽象所有任务持久化操作，使核心执行逻辑与具体存储技术解耦。
 * 默认实现基于 MyBatis-Plus + MySQL，业务方可替换为其他存储方案
 * （如 Redis、MongoDB 等）。
 *
 * <p>并发安全说明:
 * <ul>
 *   <li>fetchPendingTasks: 拉取 PENDING/RETRYING 且已到执行时间的候选任务</li>
 *   <li>claimTask: 通过状态条件和后续版本字段确保只有一个 Worker 能抢占成功</li>
 *   <li>状态更新: 通过前置状态条件防止并发状态覆盖</li>
 * </ul>
 */
public interface TaskStore {

    // ==================== 写操作 ====================

    /**
     * 保存任务
     *
     * <p>新任务首次持久化。如果 bizUniqueKey 已存在，则返回已存在任务。
     * V1.5 MVP 中同一 bizUniqueKey 永久只保留一条任务；终态后如需再次触发，
     * 业务方应使用新的 bizId 或幂等键。
     *
     * @param task 待保存的任务实例
     * @return 实际保存或已存在的任务实例
     */
    TaskInstance save(TaskInstance task);

    /**
     * 根据 ID 查询任务
     *
     * @param id 主键 ID
     * @return 任务实例，不存在时返回 null
     */
    TaskInstance getById(Long id);

    /**
     * 根据业务幂等键查询任务
     *
     * @param bizUniqueKey 幂等键 (格式: taskType:bizType:bizId)
     * @return 任务实例，不存在时返回 null
     */
    TaskInstance getByBizUniqueKey(String bizUniqueKey);

    // ==================== 调度拉取 ====================

    /**
     * 拉取待执行的任务
     *
     * <p>Worker 定时调用此方法获取可执行的任务。
     * 查询条件: status IN (PENDING, RETRYING) AND nextExecuteTime <= now
     * 排序: priority ASC, nextExecuteTime ASC
     *
     * <p>并发安全: 返回的任务不会被其他 Worker 同时拉取
     * （由调用方通过 claimTask 进行乐观锁抢占）
     *
     * @param batchSize 单次拉取数量
     * @return 待执行的任务列表
     */
    List<TaskInstance> fetchPendingTasks(int batchSize);

    /**
     * 抢占任务（乐观锁）
     *
     * <p>多实例并发安全的核心方法。
     * 将可执行任务从 PENDING/RETRYING 改为 RUNNING，
     * 只有一个 Worker 能更新成功。
     *
     * <p>状态流转: PENDING/RETRYING → RUNNING
     *
     * @param id       任务 ID
     * @param workerId 当前 Worker ID
     * @return true 表示抢占成功，false 表示已被其他 Worker 抢占
     */
    boolean claimTask(Long id, String workerId);

    /**
     * 续约正在执行中的任务锁。
     *
     * <p>默认实现为 no-op，保持 V1.5 兼容；具体存储实现可覆盖。
     *
     * @param id           任务 ID
     * @param workerId     当前 Worker ID
     * @param heartbeatTime 任务心跳时间
     * @param lockExpireAt 新的锁过期时间
     * @return 是否续约成功
     */
    default boolean renewTaskLease(Long id, String workerId,
                                   LocalDateTime heartbeatTime,
                                   LocalDateTime lockExpireAt) {
        return false;
    }

    // ==================== 执行更新 ====================

    /**
     * 更新任务为成功状态
     *
     * <p>状态流转: RUNNING → SUCCESS
     *
     * @param id 任务 ID
     * @return 是否更新成功
     */
    boolean markSuccess(Long id);

    /**
     * 更新任务为等待重试状态
     *
     * <p>状态流转: RUNNING → RETRYING
     *
     * @param id             任务 ID
     * @param errorMsg       错误信息
     * @param nextExecuteTime 下次执行时间（由重试策略计算）
     * @return 是否更新成功
     */
    boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime);

    /**
     * 更新任务为等待重试状态，并记录最近错误码。
     *
     * <p>默认兼容实现会忽略 errorCode，具体存储实现可覆盖以写入 lastErrorCode。
     *
     * @param id              任务 ID
     * @param errorCode       错误码或异常类型
     * @param errorMsg        错误信息
     * @param nextExecuteTime 下次执行时间（由重试策略计算）
     * @return 是否更新成功
     */
    default boolean markWaitRetry(Long id, String errorCode, String errorMsg, LocalDateTime nextExecuteTime) {
        return markWaitRetry(id, errorMsg, nextExecuteTime);
    }

    /**
     * 更新任务为死信状态
     *
     * <p>状态流转: RUNNING → DEAD 或 RETRYING → DEAD
     *
     * @param id       任务 ID
     * @param errorMsg 错误信息
     * @return 是否更新成功
     */
    boolean markDead(Long id, String errorMsg);

    /**
     * 更新任务为死信状态，并记录最近错误码。
     *
     * <p>默认兼容实现会忽略 errorCode，具体存储实现可覆盖以写入 lastErrorCode。
     *
     * @param id        任务 ID
     * @param errorCode 错误码或异常类型
     * @param errorMsg  错误信息
     * @return 是否更新成功
     */
    default boolean markDead(Long id, String errorCode, String errorMsg) {
        return markDead(id, errorMsg);
    }

    /**
     * 取消任务
     *
     * <p>状态流转: 任意非终态 → CANCELLED
     *
     * @param id 任务 ID
     * @return 是否取消成功（终态任务无法取消）
     */
    boolean cancelTask(Long id);

    /**
     * 手动重新入队
     *
     * <p>将 DEAD 或 CANCELLED 状态的任务重置为 PENDING，
     * 清空错误信息，重置执行次数。
     *
     * <p>状态流转: DEAD/CANCELLED → PENDING
     *
     * @param id 任务 ID
     * @return 是否重新入队成功
     */
    boolean requeueTask(Long id);

    /**
     * 更新任务 payload
     *
     * <p>用于管理后台修改待执行任务的参数。
     * 仅 PENDING 和 RETRYING 状态的任务允许修改。
     *
     * @param id      任务 ID
     * @param payload 新的 payload JSON 字符串
     * @return 是否更新成功
     */
    boolean updatePayload(Long id, String payload);

    // ==================== 超时恢复 ====================

    /**
     * 查找执行超时的任务
     *
     * <p>查找 RUNNING 状态且 lockExpireAt 早于指定时间的任务，
     * 这些任务可能是 Worker 崩溃后遗留的孤儿任务。
     *
     * @param timeoutThreshold 超时阈值（如 5 分钟前）
     * @return 超时的任务列表
     */
    List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold);

    /**
     * 重置超时任务为 PENDING 状态
     *
     * <p>将 RUNNING 超时任务恢复为 PENDING，等待 Worker 重新拉取。
     * 同时清空 workerId，保留错误信息用于排查。
     *
     * <p>状态流转: RUNNING (timeout) → PENDING
     *
     * @param id 任务 ID
     * @return 是否重置成功
     */
    boolean resetTimeoutTask(Long id);

    // ==================== 日志记录 ====================

    /**
     * 保存执行日志
     *
     * <p>每次任务执行（含重试）后记录一条日志。
     *
     * @param taskId          任务 ID
     * @param executeNo       执行序号
     * @param statusBefore    执行前状态
     * @param statusAfter     执行后状态
     * @param success         是否成功
     * @param durationMs      执行耗时（毫秒）
     * @param errorCode       错误码（成功时为 null）
     * @param errorMessage    错误信息（成功时为 null）
     */
    default void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                         boolean success, long durationMs, String errorCode, String errorMessage) {
        saveLog(taskId, executeNo, statusBefore, statusAfter,
                success, durationMs, errorCode, errorMessage, null, null);
    }

    /**
     * 保存执行日志
     *
     * <p>每次任务执行（含重试）后记录一条日志。
     *
     * @param taskId          任务 ID
     * @param executeNo       执行序号
     * @param statusBefore    执行前状态
     * @param statusAfter     执行后状态
     * @param success         是否成功
     * @param durationMs      执行耗时（毫秒）
     * @param errorCode       错误码（成功时为 null）
     * @param errorMessage    错误信息（成功时为 null）
     * @param workerId        执行节点 ID
     * @param traceId         链路追踪 ID
     */
    void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                 boolean success, long durationMs, String errorCode, String errorMessage,
                 String workerId, String traceId);

    // ==================== 查询操作 ====================

    /**
     * 分页查询任务列表
     *
     * @param request 查询条件（支持 taskType、status、bizType、时间范围等）
     * @return 分页结果
     */
    PageResult<TaskVO> listTasks(TaskQueryRequest request);

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    TaskDetailVO getTaskDetail(Long id);

    /**
     * 查询任务执行日志
     *
     * @param taskId 任务 ID
     * @return 执行日志列表（按执行序号倒序）
     */
    List<TaskLogVO> getTaskLogs(Long taskId);

    /**
     * 获取任务统计
     *
     * @return 各状态任务数量及按 taskType 分组统计
     */
    TaskStatsVO getStats();

    // ==================== V2 运维能力 ====================

    /**
     * 上报 Worker 心跳。
     *
     * <p>默认实现为 no-op，保持 V1.5 兼容；具体存储实现可覆盖。
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
