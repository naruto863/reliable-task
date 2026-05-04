package com.reliabletask.core.annotation;

import com.reliabletask.core.enums.RetryStrategyType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务重试策略注解
 *
 * <p>标注在 TaskHandler 实现类上，声明该 Handler 处理任务的重试行为。
 * 注解参数优先级高于 TaskInstance 中的配置。
 *
 * <p>使用示例:
 * <pre>
 * &#64;TaskRetryable(
 *     maxRetryCount = 5,
 *     strategy = RetryStrategyType.EXPONENTIAL,
 *     retryIntervalMs = 2000,
 *     maxDelayMs = 60000
 * )
 * public class CreateShipmentHandler implements TaskHandler {
 *     // ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskRetryable {

    /**
     * 最大重试次数（不含首次执行）
     *
     * <p>默认 -1 表示使用 TaskInstance 中的配置；0 表示不重试。
     */
    int maxRetryCount() default -1;

    /**
     * 重试策略类型
     *
     * <p>默认 null 表示使用 TaskInstance 中的配置，未配置时使用 EXPONENTIAL。
     */
    RetryStrategyType strategy() default RetryStrategyType.EXPONENTIAL;

    /**
     * 基础重试间隔，单位毫秒
     *
     * <p>默认 -1 表示使用 TaskInstance 中的配置。
     */
    long retryIntervalMs() default -1;

    /**
     * 最大延迟上限，单位毫秒
     *
     * <p>防止指数退避延迟无限增长。
     * <p>默认 -1 表示使用框架默认值（300000ms = 5 分钟）。
     */
    long maxDelayMs() default -1;
}
