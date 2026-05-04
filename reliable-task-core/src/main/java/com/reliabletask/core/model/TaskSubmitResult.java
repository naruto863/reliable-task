package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务投递结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmitResult {

    private Long taskId;

    private String fallbackId;

    private String taskType;

    private String bizId;

    private String bizUniqueKey;

    private boolean created;

    private boolean existing;

    private String idempotencyStrategy;

    private String reason;

    public String getResultId() {
        return taskId != null ? String.valueOf(taskId) : fallbackId;
    }
}
