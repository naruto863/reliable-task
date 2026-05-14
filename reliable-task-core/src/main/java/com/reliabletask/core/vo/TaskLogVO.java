package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行日志视图对象
 *
 * <p>用于管理后台查看任务的每次执行历史记录。
 */
@Data
public class TaskLogVO {

    /**
     * 日志主键 ID
     */
    private Long id;

    /**
     * 关联的任务 ID
     */
    private Long taskId;

    /**
     * 本次执行序号
     */
    private Integer attemptNo;

    /**
     * 执行前任务状态
     */
    private String statusBefore;

    /**
     * 执行后任务状态
     */
    private String statusAfter;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 执行结果状态码
     */
    private Integer status;

    /**
     * 执行结果状态描述
     */
    private String statusDesc;

    /**
     * 错误码或异常类型
     */
    private String errorCode;

    /**
     * 异常信息
     */
    private String errorMsg;

    /**
     * 执行节点 ID
     */
    private String workerId;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;
}
