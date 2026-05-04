package com.reliabletask.core.model;

import com.reliabletask.core.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行指标事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionMetricsEvent {

    private Long taskId;

    private String taskType;

    private TaskStatus status;

    private String workerId;

    private long durationMs;

    private boolean success;

    private int retryCount;

    private String errorCode;

    private String traceId;

    private LocalDateTime eventTime;
}
