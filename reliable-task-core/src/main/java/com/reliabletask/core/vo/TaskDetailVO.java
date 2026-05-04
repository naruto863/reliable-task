package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务详情视图对象
 *
 * <p>用于管理后台任务详情页，包含任务的完整信息。
 */
@Data
public class TaskDetailVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务唯一标识
     */
    private String bizId;

    /**
     * 幂等键
     */
    private String bizUniqueKey;

    /**
     * 任务状态码
     */
    private Integer statusCode;

    /**
     * 任务状态描述
     */
    private String statusDesc;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 任务参数 JSON
     */
    private String payload;

    /**
     * 已执行次数
     */
    private Integer executeCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 重试策略
     */
    private String retryStrategy;

    /**
     * 基础重试间隔（毫秒）
     */
    private Long retryIntervalMs;

    /**
     * 下次执行时间
     */
    private LocalDateTime nextExecuteTime;

    /**
     * 分片键
     */
    private String shardKey;

    /**
     * 租户标识
     */
    private String tenantId;

    /**
     * 执行节点 ID
     */
    private String workerId;

    /**
     * 异常信息
     */
    private String errorMsg;

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
     * 完成时间
     */
    private LocalDateTime finishTime;
}
