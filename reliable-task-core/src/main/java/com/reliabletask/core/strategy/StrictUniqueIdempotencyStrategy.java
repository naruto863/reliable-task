package com.reliabletask.core.strategy;

import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;
import com.reliabletask.core.spi.IdempotencyStrategy;

/**
 * 严格唯一幂等策略。
 *
 * <p>同一个 bizUniqueKey 永远只对应一条任务，保持 V1.5 默认行为。
 */
public class StrictUniqueIdempotencyStrategy implements IdempotencyStrategy {

    public static final String NAME = "STRICT_UNIQUE";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public IdempotencyDecision decide(IdempotencyContext context) {
        if (context != null && context.getExistingTask() != null) {
            return IdempotencyDecision.returnExisting(context.getExistingTask().getId());
        }
        return IdempotencyDecision.createNew(context == null ? null : context.getBizUniqueKey());
    }
}
