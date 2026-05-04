package com.reliabletask.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 重试策略类型枚举
 *
 * <p>用于控制任务失败后的重试间隔计算方式。
 */
@Getter
@AllArgsConstructor
public enum RetryStrategyType {

    /**
     * 固定间隔重试：每次重试间隔相同
     *
     * <p>计算公式: delay = baseIntervalMs
     * <p>示例: baseIntervalMs=2000 → 2s, 2s, 2s, 2s...
     */
    FIXED("固定间隔"),

    /**
     * 指数退避重试：每次重试间隔按倍数递增
     *
     * <p>计算公式: delay = baseIntervalMs * multiplier ^ retryCount
     * <p>示例: baseIntervalMs=1000, multiplier=2 → 1s, 2s, 4s, 8s, 16s...
     */
    EXPONENTIAL("指数退避"),

    /**
     * 自定义策略：由业务方自行实现 RetryStrategy 接口
     */
    CUSTOM("自定义");

    /**
     * 策略描述
     */
    private final String description;
}
