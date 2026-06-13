package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务命令存储 SPI。
 *
 * <p>覆盖投递、Worker 拉取、抢占、租约续约、执行结果回写、超时恢复和执行日志写入。
 * executor/template/retry/recovery 等执行链路应优先依赖本接口，避免被 Admin 查询和人工运维能力耦合。
 */
public interface TaskCommandStore {

    /**
     * 保存任务。
     *
     * <p>新任务首次持久化。如果 bizUniqueKey 已存在，则返回已存在任务。
     *
     * @param task 待保存的任务实例
     * @return 实际保存或已存在的任务实例
     */
    TaskInstance save(TaskInstance task);

    /**
     * 根据 ID 查询任务。
     *
     * @param id 主键 ID
     * @return 任务实例，不存在时返回 null
     */
    TaskInstance getById(Long id);

    /**
     * 根据业务幂等键查询任务。
     *
     * @param bizUniqueKey 幂等键
     * @return 任务实例，不存在时返回 null
     */
    TaskInstance getByBizUniqueKey(String bizUniqueKey);

    /**
     * 拉取待执行的任务。
     *
     * @param batchSize 单次拉取数量
     * @return 待执行的任务列表
     */
    List<TaskInstance> fetchPendingTasks(int batchSize);

    /**
     * 抢占任务。
     *
     * @param id       任务 ID
     * @param workerId 当前 Worker ID
     * @return true 表示抢占成功
     */
    boolean claimTask(Long id, String workerId);

    /**
     * 抢占任务，并指定本次执行锁的过期时间。
     *
     * <p>默认委托旧接口，保持自定义存储实现的源码兼容；支持锁 TTL 的存储实现应覆盖此方法。
     *
     * @param id           任务 ID
     * @param workerId     当前 Worker ID
     * @param lockExpireAt 本次锁过期时间
     * @return true 表示抢占成功
     */
    default boolean claimTask(Long id, String workerId, LocalDateTime lockExpireAt) {
        return claimTask(id, workerId);
    }

    /**
     * 续约正在执行中的任务锁。
     *
     * @param id            任务 ID
     * @param workerId      当前 Worker ID
     * @param heartbeatTime 任务心跳时间
     * @param lockExpireAt  新的锁过期时间
     * @return 是否续约成功
     */
    default boolean renewTaskLease(Long id, String workerId,
                                   LocalDateTime heartbeatTime,
                                   LocalDateTime lockExpireAt) {
        return false;
    }

    /**
     * 更新任务为成功状态。
     *
     * @param id 任务 ID
     * @return 是否更新成功
     */
    boolean markSuccess(Long id);

    /**
     * 基于执行租约更新任务为成功状态。
     *
     * @param lease 执行租约
     * @return 是否更新成功
     */
    default boolean markSuccess(TaskExecutionLease lease) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return markSuccess(lease.getTaskId());
    }

    /**
     * 更新任务为等待重试状态。
     *
     * @param id              任务 ID
     * @param errorMsg        错误信息
     * @param nextExecuteTime 下次执行时间
     * @return 是否更新成功
     */
    boolean markWaitRetry(Long id, String errorMsg, LocalDateTime nextExecuteTime);

    /**
     * 更新任务为等待重试状态，并记录最近错误码。
     *
     * @param id              任务 ID
     * @param errorCode       错误码或异常类型
     * @param errorMsg        错误信息
     * @param nextExecuteTime 下次执行时间
     * @return 是否更新成功
     */
    default boolean markWaitRetry(Long id, String errorCode, String errorMsg, LocalDateTime nextExecuteTime) {
        return markWaitRetry(id, errorMsg, nextExecuteTime);
    }

    /**
     * 基于执行租约更新任务为等待重试状态。
     *
     * @param lease           执行租约
     * @param errorMsg        错误信息
     * @param nextExecuteTime 下次执行时间
     * @return 是否更新成功
     */
    default boolean markWaitRetry(TaskExecutionLease lease, String errorMsg, LocalDateTime nextExecuteTime) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return markWaitRetry(lease.getTaskId(), errorMsg, nextExecuteTime);
    }

    /**
     * 基于执行租约更新任务为等待重试状态，并记录最近错误码。
     *
     * @param lease           执行租约
     * @param errorCode       错误码或异常类型
     * @param errorMsg        错误信息
     * @param nextExecuteTime 下次执行时间
     * @return 是否更新成功
     */
    default boolean markWaitRetry(TaskExecutionLease lease, String errorCode, String errorMsg,
                                  LocalDateTime nextExecuteTime) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return markWaitRetry(lease.getTaskId(), errorCode, errorMsg, nextExecuteTime);
    }

    /**
     * 更新任务为死信状态。
     *
     * @param id       任务 ID
     * @param errorMsg 错误信息
     * @return 是否更新成功
     */
    boolean markDead(Long id, String errorMsg);

    /**
     * 更新任务为死信状态，并记录最近错误码。
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
     * 基于执行租约更新任务为死信状态。
     *
     * @param lease    执行租约
     * @param errorMsg 错误信息
     * @return 是否更新成功
     */
    default boolean markDead(TaskExecutionLease lease, String errorMsg) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return markDead(lease.getTaskId(), errorMsg);
    }

    /**
     * 基于执行租约更新任务为死信状态，并记录最近错误码。
     *
     * @param lease     执行租约
     * @param errorCode 错误码或异常类型
     * @param errorMsg  错误信息
     * @return 是否更新成功
     */
    default boolean markDead(TaskExecutionLease lease, String errorCode, String errorMsg) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return markDead(lease.getTaskId(), errorCode, errorMsg);
    }

    /**
     * 查找执行超时的任务。
     *
     * @param timeoutThreshold 锁过期阈值
     * @return 超时的任务列表
     */
    List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold);

    /**
     * 查找执行超时的任务，并限制单次扫描数量。
     *
     * @param timeoutThreshold 超时阈值
     * @param limit            最大返回数量
     * @return 超时的任务列表
     */
    default List<TaskInstance> findTimeoutTasks(LocalDateTime timeoutThreshold, int limit) {
        List<TaskInstance> tasks = findTimeoutTasks(timeoutThreshold);
        if (tasks == null || limit <= 0 || tasks.size() <= limit) {
            return tasks;
        }
        return tasks.subList(0, limit);
    }

    /**
     * 重置超时任务为 PENDING 状态。
     *
     * @param id 任务 ID
     * @return 是否重置成功
     */
    boolean resetTimeoutTask(Long id);

    /**
     * 基于执行租约重置超时任务为 PENDING 状态。
     *
     * @param lease 执行租约
     * @return 是否重置成功
     */
    default boolean resetTimeoutTask(TaskExecutionLease lease) {
        if (lease == null || lease.getTaskId() == null) {
            return false;
        }
        return resetTimeoutTask(lease.getTaskId());
    }

    /**
     * 保存执行日志。
     *
     * @param taskId       任务 ID
     * @param executeNo    执行序号
     * @param statusBefore 执行前状态
     * @param statusAfter  执行后状态
     * @param success      是否成功
     * @param durationMs   执行耗时
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     */
    default void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                         boolean success, long durationMs, String errorCode, String errorMessage) {
        saveLog(taskId, executeNo, statusBefore, statusAfter,
                success, durationMs, errorCode, errorMessage, null, null);
    }

    /**
     * 保存执行日志。
     *
     * @param taskId       任务 ID
     * @param executeNo    执行序号
     * @param statusBefore 执行前状态
     * @param statusAfter  执行后状态
     * @param success      是否成功
     * @param durationMs   执行耗时
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param workerId     执行节点 ID
     * @param traceId      链路追踪 ID
     */
    void saveLog(Long taskId, int executeNo, String statusBefore, String statusAfter,
                 boolean success, long durationMs, String errorCode, String errorMessage,
                 String workerId, String traceId);
}
