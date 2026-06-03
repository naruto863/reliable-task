package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 幂等决策上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyContext {

    private String taskType;

    private String bizType;

    private String bizId;

    private String bizUniqueKey;

    private String idempotencyKey;

    private String payload;

    private String strategyName;

    private TaskInstance existingTask;
}
