package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近失败查询结果。
 */
@Data
public class TaskFailureVO {

    private Long taskId;

    private String taskType;

    private String bizType;

    private String bizId;

    private String statusAfter;

    private String errorCode;

    private String errorMsg;

    private Long durationMs;

    private String workerId;

    private String traceId;

    private LocalDateTime executeTime;
}
