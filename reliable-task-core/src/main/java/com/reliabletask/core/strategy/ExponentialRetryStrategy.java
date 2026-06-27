package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * 指数退避重试策略
 *
 * <p>每次重试延迟按指数增长，避免重试风暴。
 *
 * <p>计算公式: delay = min(intervalMs * multiplier ^ retryCount, maxDelayMs)
 * <p>示例: intervalMs=1000, multiplier=2.0 → 1s, 2s, 4s, 8s, 16s...
 *
 * <p>默认 multiplier 为 2.0，可通过构造函数自定义。
 *
 * <p>当开启 jitter 时，会在已封顶的延迟值上下浮动，最后再次受 maxDelayMs 约束。
 * 这样既能削峰，避免多个 Worker 同时重试，又不会突破用户声明的最大等待时间。
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

    /**
     * 抖动比例，0 表示关闭抖动。
     */
    private final double jitterRatio;

    private final DoubleSupplier randomSupplier;

    public ExponentialRetryStrategy() {
        this(DEFAULT_MULTIPLIER, 0.0D);
    }

    public ExponentialRetryStrategy(double multiplier) {
        this(multiplier, 0.0D);
    }

    public ExponentialRetryStrategy(double multiplier, double jitterRatio) {
        this(multiplier, jitterRatio, () -> ThreadLocalRandom.current().nextDouble());
    }

    public ExponentialRetryStrategy(double multiplier, double jitterRatio, DoubleSupplier randomSupplier) {
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("multiplier must be greater than 1.0");
        }
        if (jitterRatio < 0.0D || jitterRatio > 1.0D) {
            throw new IllegalArgumentException("jitterRatio must be between 0.0 and 1.0");
        }
        this.multiplier = multiplier;
        this.jitterRatio = jitterRatio;
        this.randomSupplier = randomSupplier != null
                ? randomSupplier
                : () -> ThreadLocalRandom.current().nextDouble();
    }

    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.EXPONENTIAL;
    }

    @Override
    public long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs) {
        double delay = intervalMs * Math.pow(multiplier, retryCount);
        long cappedDelay = (long) Math.min(delay, maxDelayMs);
        if (jitterRatio == 0.0D || cappedDelay <= 0L) {
            return Math.max(cappedDelay, 0L);
        }

        // randomSupplier 主要服务测试和可插拔随机源；归一化可防御异常实现返回 NaN 或越界值。
        double random = normalizeRandom(randomSupplier.getAsDouble());
        double min = cappedDelay * (1.0D - jitterRatio);
        double max = cappedDelay * (1.0D + jitterRatio);
        long jitteredDelay = (long) (min + (max - min) * random);
        return Math.max(0L, Math.min(jitteredDelay, maxDelayMs));
    }

    private double normalizeRandom(double random) {
        if (Double.isNaN(random)) {
            return 0.5D;
        }
        return Math.max(0.0D, Math.min(1.0D, random));
    }
}
