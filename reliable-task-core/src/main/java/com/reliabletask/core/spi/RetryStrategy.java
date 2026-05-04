package com.reliabletask.core.spi;

import com.reliabletask.core.enums.RetryStrategyType;

/**
 * 重试策略 SPI
 *
 * <p>定义任务失败后的重试延迟计算逻辑和可重试判断。
 * 框架内置了固定间隔 (FIXED) 和指数退避 (EXPONENTIAL) 两种策略，
 * 业务方可实现此接口自定义重试行为。
 *
 * <p>每次重试的延迟由 {@link #nextDelayMs(int, long, long)} 计算，
 * 可重试性由 {@link #isRetryable(Exception)} 判断。
 */
public interface RetryStrategy {

    /**
     * 获取此策略的类型标识
     *
     * @return 重试策略类型
     */
    RetryStrategyType getType();

    /**
     * 计算下一次重试的延迟时间
     *
     * <p>状态流转说明:
     * <pre>
     *   任务执行失败 → 调用此方法计算延迟 → 设置 nextExecuteTime = now + delay
     *   → 状态变为 RETRYING → 到达 nextExecuteTime 后重新进入 RUNNING
     * </pre>
     *
     * @param retryCount   当前已重试次数（从 0 开始，0 表示第一次重试）
     * @param intervalMs   基础重试间隔（由 TaskSubmitRequest 或配置指定）
     * @param maxDelayMs   最大延迟上限（防止延迟无限增长）
     * @return 下一次重试的延迟时间，单位毫秒；返回 <= 0 表示立即重试
     */
    long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs);

    /**
     * 判断异常是否可重试
     *
     * <p>默认实现:
     * <ul>
     *   <li>NonRetryableException → 不可重试</li>
     *   <li>其他异常 → 可重试</li>
     * </ul>
     *
     * <p>业务方可覆盖此方法实现自定义判断逻辑，
     * 例如根据 HTTP 状态码、错误码等决定是否重试。
     *
     * @param ex 执行过程中抛出的异常
     * @return true 表示可以重试，false 表示直接进入 DEAD
     */
    default boolean isRetryable(Exception ex) {
        return !(ex instanceof com.reliabletask.core.exception.NonRetryableException);
    }
}
