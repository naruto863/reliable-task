package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;

/**
 * 固定间隔重试策略
 *
 * <p>每次重试使用相同的延迟时间，由 intervalMs 参数决定。
 *
 * <p>计算公式: delay = intervalMs
 * <p>示例: intervalMs=2000 → 2s, 2s, 2s, 2s...
 */
public class FixedRetryStrategy implements RetryStrategy {

    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.FIXED;
    }

    @Override
    public long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs) {
        return Math.min(intervalMs, maxDelayMs);
    }
}
