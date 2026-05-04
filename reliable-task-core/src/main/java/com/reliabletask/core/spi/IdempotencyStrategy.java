package com.reliabletask.core.spi;

import com.reliabletask.core.model.IdempotencyContext;
import com.reliabletask.core.model.IdempotencyDecision;

/**
 * 任务投递幂等策略 SPI。
 */
public interface IdempotencyStrategy {

    /**
     * 策略名称。
     *
     * @return 稳定策略名称
     */
    String getName();

    /**
     * 根据当前投递上下文做幂等决策。
     *
     * @param context 幂等上下文
     * @return 决策结果
     */
    IdempotencyDecision decide(IdempotencyContext context);
}
