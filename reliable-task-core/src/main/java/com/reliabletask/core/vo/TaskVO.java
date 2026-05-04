package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务列表视图对象
 *
 * <p>用于管理后台任务列表展示，只包含列表页需要的核心字段。
 * 详细信息通过 TaskDetailVO 获取。
 */
@Data
public class TaskVO {

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
     * 已执行次数
     */
    private Integer executeCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 最近一次异常信息（截断显示）
     */
    private String errorMsg;

    /**
     * 执行节点 ID
     */
    private String workerId;

    /**
     * 下次执行时间
     */
    private LocalDateTime nextExecuteTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;
}
