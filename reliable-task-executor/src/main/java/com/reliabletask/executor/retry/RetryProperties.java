package com.reliabletask.executor.retry;

import lombok.Data;

/**
 * RetryEngine 运行时配置。
 *
 * <p>这里承载跨任务的默认退避参数。单个 Handler 的 @TaskRetryable 和任务提交时的
 * TaskInstance 配置仍可能覆盖部分字段，最终优先级由 RetryStrategyResolver 统一决定。
 */
@Data
public class RetryProperties {

    /**
     * 指数退避增长倍数，默认 2.0。
     */
    private double exponentialMultiplier = 2.0D;

    /**
     * 指数退避抖动比例，0 表示关闭抖动。
     *
     * <p>建议在高并发失败场景开启少量抖动，避免大量任务在同一时间点同时重试。
     */
    private double jitterRatio = 0.0D;

    /**
     * 最小重试延迟，单位毫秒，默认 0。
     *
     * <p>用于防止计算结果过小导致快速空转；解析阶段会保证它不超过 maxDelayMs。
     */
    private long minDelayMs = 0L;

    /**
     * 最大重试延迟，单位毫秒，默认 5 分钟。
     */
    private long maxDelayMs = 300_000L;
}
