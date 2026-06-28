package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 幂等策略决策结果。
 *
 * <p>策略只返回决策，不直接写库。模板层会根据 action 决定创建新任务、返回已有任务或拒绝提交，
 * 存储层仍通过唯一键兜底处理并发重复投递。
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
        /**
         * 创建新任务，可选覆盖最终 bizUniqueKey。
         */
        CREATE_NEW,
        /**
         * 直接返回已有任务 ID，不新建任务。
         */
        RETURN_EXISTING,
        /**
         * 拒绝本次投递，reason 会作为业务错误说明。
         */
        REJECT
    }
}
