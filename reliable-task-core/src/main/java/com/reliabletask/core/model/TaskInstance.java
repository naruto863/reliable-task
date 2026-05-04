package com.reliabletask.core.model;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务实例领域实体
 *
 * <p>对应数据库表 reliable_task，是组件内部流转的核心领域对象。
 * 所有任务的生命周期操作都围绕此实体进行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstance {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 任务类型（如 CREATE_SHIPMENT）
     * 用于路由到对应的 TaskHandler 实现
     */
    private String taskType;

    /**
     * 业务类型（如 ORDER/USER/PAYMENT）
     */
    private String bizType;

    /**
     * 业务唯一标识（如订单号）
     */
    private String bizId;

    /**
     * 幂等键，格式: task_type:biz_type:biz_id
     * 保证同一业务动作不会被重复投递
     */
    private String bizUniqueKey;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 优先级，0-9，数字越小优先级越高
     */
    private Integer priority;

    /**
     * 任务参数，JSON 格式字符串
     * 由业务方在投递时传入，TaskHandler 执行时反序列化使用
     */
    private String payload;

    /**
     * 已执行次数（含首次执行和所有重试）
     */
    private Integer executeCount;

    /**
     * 乐观锁版本号，用于并发抢占和状态更新
     */
    private Integer version;

    /**
     * 最大重试次数（不含首次执行）。
     *
     * <p>例如 maxRetryCount = 3 表示最多执行 1 次首次尝试 + 3 次重试。
     */
    private Integer maxRetryCount;

    /**
     * 重试策略
     */
    private RetryStrategyType retryStrategy;

    /**
     * 基础重试间隔，单位毫秒
     */
    private Long retryIntervalMs;

    /**
     * 下次执行时间
     * - 立即执行任务: 设置为当前时间
     * - 延迟任务: 设置为当前时间 + 延迟
     * - 重试任务: 根据重试策略计算得出
     */
    private LocalDateTime nextExecuteTime;

    /**
     * 分片键（可选）
     */
    private String shardKey;

    /**
     * 租户标识（可选）
     */
    private String tenantId;

    /**
     * 当前执行节点 ID
     */
    private String workerId;

    /**
     * 任务被 Worker 抢占的时间
     */
    private LocalDateTime lockedAt;

    /**
     * 任务锁过期时间，用于识别超时 RUNNING 任务
     */
    private LocalDateTime lockExpireAt;

    /**
     * Worker 执行任务期间最近一次心跳时间
     */
    private LocalDateTime heartbeatTime;

    /**
     * 最近一次开始执行时间
     */
    private LocalDateTime lastExecuteTime;

    /**
     * 最近一次执行的异常信息
     */
    private String errorMsg;

    /**
     * 最近一次执行失败的错误码或异常类型
     */
    private String lastErrorCode;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 完成时间（成功/失败/取消时记录）
     */
    private LocalDateTime finishTime;

    /**
     * 判断是否可以继续重试
     *
     * <p>executeCount 包含首次执行，maxRetryCount 只表示首次执行后的重试次数。
     *
     * @return true 如果实际已重试次数未达到最大重试次数
     */
    public boolean canRetry() {
        if (maxRetryCount == null || maxRetryCount <= 0) {
            return false;
        }
        return getActualRetryCount() < maxRetryCount;
    }

    /**
     * 获取实际已重试次数
     *
     * <p>executeCount 包含首次执行，所以重试次数 = executeCount - 1
     * 如果 executeCount 为 0（尚未执行），则重试次数为 0
     */
    public int getActualRetryCount() {
        return Math.max(0, executeCount - 1);
    }
}
