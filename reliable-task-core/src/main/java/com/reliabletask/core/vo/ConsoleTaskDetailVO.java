package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 控制台安全任务详情视图。
 *
 * <p>不包含原始 payload 字段，payload 展示统一通过 {@link PayloadViewVO} 表达。
 */
@Data
public class ConsoleTaskDetailVO {

    private Long id;

    private String taskType;

    private String bizType;

    private String bizId;

    private String bizUniqueKey;

    private Integer statusCode;

    private String statusDesc;

    private Integer priority;

    private PayloadViewVO payloadView;

    private Integer executeCount;

    private Integer maxRetryCount;

    private String retryStrategy;

    private Long retryIntervalMs;

    private LocalDateTime nextExecuteTime;

    private String shardKey;

    private String tenantId;

    private String workerId;

    private String errorMsg;

    private String traceId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime finishTime;
}
