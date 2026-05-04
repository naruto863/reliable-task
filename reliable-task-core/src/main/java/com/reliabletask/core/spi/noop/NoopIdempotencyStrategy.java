package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.spi.IdempotencyStrategy;

/**
 * 兼容默认幂等策略：未命中已有任务时允许创建，命中时返回已有任务。
 */
public class NoopIdempotencyStrategy implements IdempotencyStrategy {

    public static final String NAME = "NOOP";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public IdempotencyDecision decide(IdempotencyContext context) {
        if (context != null && context.getExistingTask() != null) {
            return IdempotencyDecision.returnExisting(context.getExistingTask().getId());
        }
        return IdempotencyDecision.createNew();
    }
}
