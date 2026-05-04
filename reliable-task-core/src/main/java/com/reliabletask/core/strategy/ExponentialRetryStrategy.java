package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;

/**
 * 指数退避重试策略
 *
 * <p>每次重试延迟按指数增长，避免重试风暴。
 *
 * <p>计算公式: delay = min(intervalMs * multiplier ^ retryCount, maxDelayMs)
 * <p>示例: intervalMs=1000, multiplier=2.0 → 1s, 2s, 4s, 8s, 16s...
 *
 * <p>默认 multiplier 为 2.0，可通过构造函数自定义。
 */
public class ExponentialRetryStrategy implements RetryStrategy {

    /**
     * 默认增长倍数
     */
    private static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * 延迟增长倍数
     */
    private final double multiplier;

    public ExponentialRetryStrategy() {
        this(DEFAULT_MULTIPLIER);
    }

    public ExponentialRetryStrategy(double multiplier) {
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("multiplier must be greater than 1.0");
        }
        this.multiplier = multiplier;
    }

    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.EXPONENTIAL;
    }

    @Override
    public long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs) {
        double delay = intervalMs * Math.pow(multiplier, retryCount);
        return (long) Math.min(delay, maxDelayMs);
    }
}
