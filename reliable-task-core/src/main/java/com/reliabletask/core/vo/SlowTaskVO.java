package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 慢任务查询结果。
 */
@Data
public class SlowTaskVO {

    private Long taskId;

    private String taskType;

    private String bizType;

    private String bizId;

    private String statusBefore;

    private String statusAfter;

    private String errorCode;

    private String errorMsg;

    private Long durationMs;

    private String workerId;

    private String traceId;

    private LocalDateTime executeTime;
}
