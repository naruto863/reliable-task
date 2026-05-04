package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 幂等策略决策结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyDecision {

    private Action action;

    private Long existingTaskId;

    private String bizUniqueKey;

    private String reason;

    public static IdempotencyDecision createNew() {
        return IdempotencyDecision.builder()
                .action(Action.CREATE_NEW)
                .build();
    }

    public static IdempotencyDecision createNew(String bizUniqueKey) {
        return IdempotencyDecision.builder()
                .action(Action.CREATE_NEW)
                .bizUniqueKey(bizUniqueKey)
                .build();
    }

    public static IdempotencyDecision returnExisting(Long existingTaskId) {
        return IdempotencyDecision.builder()
                .action(Action.RETURN_EXISTING)
                .existingTaskId(existingTaskId)
                .build();
    }

    public static IdempotencyDecision reject(String reason) {
        return IdempotencyDecision.builder()
                .action(Action.REJECT)
                .reason(reason)
                .build();
    }

    public boolean shouldCreateNew() {
        return action == Action.CREATE_NEW;
    }

    public enum Action {
        CREATE_NEW,
        RETURN_EXISTING,
        REJECT
    }
}
