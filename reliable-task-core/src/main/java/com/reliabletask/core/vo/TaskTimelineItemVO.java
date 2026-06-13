package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务生命周期时间线条目。
 */
@Data
public class TaskTimelineItemVO {

    /**
     * 时间线来源：SUBMITTED、LOG、AUDIT、CURRENT。
     */
    private String source;

    /**
     * 事件类型，例如 TASK_SUBMITTED、TASK_EXECUTION、ADMIN_OPERATION、CURRENT_STATE。
     */
    private String eventType;

    /**
     * 来源记录 ID。主表事件使用任务 ID，执行日志和审计日志使用各自记录 ID。
     */
    private Long sourceId;

    /**
     * 关联任务 ID。
     */
    private Long taskId;

    /**
     * 事件发生时间。
     */
    private LocalDateTime eventTime;

    /**
     * 执行前任务状态。
     */
    private String statusBefore;

    /**
     * 执行后或当前任务状态。
     */
    private String statusAfter;

    /**
     * 当前或执行结果状态码。
     */
    private Integer statusCode;

    /**
     * 当前或执行结果状态描述。
     */
    private String statusDesc;

    /**
     * 执行序号。
     */
    private Integer attemptNo;

    /**
     * 执行耗时，单位毫秒。
     */
    private Long durationMs;

    /**
     * 错误码或异常类型。
     */
    private String errorCode;

    /**
     * 错误摘要。
     */
    private String errorMsg;

    /**
     * 执行 Worker ID。
     */
    private String workerId;

    /**
     * Admin 操作人。
     */
    private String operator;

    /**
     * Admin 操作类型。
     */
    private String operationType;

    /**
     * Admin 请求摘要。
     */
    private String requestSummary;

    /**
     * Admin 操作结果。
     */
    private String result;

    /**
     * 链路追踪 ID。
     */
    private String traceId;
}
