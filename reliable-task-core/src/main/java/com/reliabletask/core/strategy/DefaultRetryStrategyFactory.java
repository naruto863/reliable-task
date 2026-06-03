package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;

/**
 * 默认重试策略工厂
 *
 * <p>根据 RetryStrategyType 返回对应的 RetryStrategy 实例。
 * 策略对象无状态，使用单例复用。
 *
 * <p>状态流转说明:
 * <pre>
 *   任务执行失败 → 工厂获取对应策略 → 策略计算 nextDelayMs
 *   → 设置 nextExecuteTime = now + delay → 进入 RETRYING 状态
 * </pre>
 */
public class DefaultRetryStrategyFactory {

    private static final FixedRetryStrategy FIXED_STRATEGY = new FixedRetryStrategy();
    private static final ExponentialRetryStrategy EXPONENTIAL_STRATEGY = new ExponentialRetryStrategy();
    private static final RetryStrategyRegistry DEFAULT_REGISTRY =
            new RetryStrategyRegistry(FIXED_STRATEGY, EXPONENTIAL_STRATEGY, java.util.List.of());

    private DefaultRetryStrategyFactory() {
    }

    /**
     * 根据类型获取对应的重试策略实例
     *
     * @param type 重试策略类型
     * @return 对应的 RetryStrategy 实现
     * @throws IllegalArgumentException 当 type 为 CUSTOM 且未注册自定义策略时
     */
    public static RetryStrategy getStrategy(RetryStrategyType type) {
        return DEFAULT_REGISTRY.getStrategy(type);
    }

    /**
     * 获取指数退避策略（默认策略）
     */
    public static RetryStrategy exponential() {
        return EXPONENTIAL_STRATEGY;
    }

    /**
     * 获取固定间隔策略
     */
    public static RetryStrategy fixed() {
        return FIXED_STRATEGY;
    }
}
