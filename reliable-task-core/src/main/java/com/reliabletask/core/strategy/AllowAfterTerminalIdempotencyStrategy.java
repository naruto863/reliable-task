package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.IdempotencyStrategy;

/**
 * 终态后允许再次投递的幂等策略。
 *
 * <p>如果同一 bizUniqueKey 对应的旧任务仍在非终态，返回已有任务；如果旧任务已结束，
 * 生成带后缀的新幂等键，绕开唯一键限制并创建新任务。
 */
public class AllowAfterTerminalIdempotencyStrategy implements IdempotencyStrategy {

    public static final String NAME = "ALLOW_AFTER_TERMINAL";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public IdempotencyDecision decide(IdempotencyContext context) {
        if (context == null || context.getExistingTask() == null) {
            return IdempotencyDecision.createNew(context == null ? null : context.getBizUniqueKey());
        }

        TaskInstance existing = context.getExistingTask();
        if (isTerminal(existing.getStatus())) {
            String uniqueKey = context.getBizUniqueKey()
                    + ":RERUN:" + existing.getId()
                    + ":" + System.nanoTime();
            return IdempotencyDecision.createNew(uniqueKey);
        }
        return IdempotencyDecision.returnExisting(existing.getId());
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.SUCCESS
                || status == TaskStatus.DEAD
                || status == TaskStatus.CANCELLED;
    }
}
