package com.reliabletask.executor.retry;

import lombok.Data;

/**
 * RetryEngine 运行时配置。
 */
@Data
public class RetryProperties {

    /**
     * 指数退避增长倍数，默认 2.0。
     */
    private double exponentialMultiplier = 2.0D;

    /**
     * 指数退避抖动比例，0 表示关闭抖动。
     */
    private double jitterRatio = 0.0D;

    /**
     * 最小重试延迟，单位毫秒，默认 0。
     */
    private long minDelayMs = 0L;

    /**
     * 最大重试延迟，单位毫秒，默认 5 分钟。
     */
    private long maxDelayMs = 300_000L;
}
