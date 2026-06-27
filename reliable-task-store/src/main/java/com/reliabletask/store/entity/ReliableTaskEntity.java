package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务主表 Entity
 *
 * <p>对应数据库表 reliable_task，存储异步任务实例的完整信息。
 *
 * <p>Entity 只表达数据库列结构。状态流转、幂等、租约 CAS 和恢复条件不要写在实体上，
 * 统一放在 TaskStore/TaskStateMachine，避免持久化模型承载业务行为。
 */
@Data
@TableName("reliable_task")
public class ReliableTaskEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型（如 CREATE_SHIPMENT）
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
     *
     * <p>显式 idempotencyKey 会覆盖默认格式，因此这里保存的是最终入库键，而不是固定拼接结果。
     */
    private String bizUniqueKey;

    /**
     * 任务状态: 0-PENDING 1-RUNNING 2-SUCCESS 3-FAILED 4-RETRYING 5-DEAD 6-CANCELLED
     */
    private Integer status;

    /**
     * 优先级，0-9，数字越小优先级越高
     */
    private Integer priority;

    /**
     * 任务参数，JSON 格式
     */
    private String payload;

    /**
     * 已执行次数（含首次执行和重试）
     */
    private Integer executeCount;

    /**
     * 乐观锁版本号，用于并发抢占和状态更新
     */
    private Integer version;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 重试策略: FIXED/EXPONENTIAL/CUSTOM
     */
    private String retryStrategy;

    /**
     * 基础重试间隔，单位毫秒
     */
    private Long retryIntervalMs;

    /**
     * 下次执行时间，用于延迟任务和重试调度
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
     *
     * <p>仅表示当前租约持有者或最近执行节点，Worker 重启后会生成新的 workerId。
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 完成时间（成功/失败/取消时记录）
     */
    private LocalDateTime finishTime;
}
