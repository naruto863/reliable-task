package com.reliabletask.executor.retry;

import com.reliabletask.core.annotation.TaskRetryable;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 重试策略解析器
 *
 * <p>从 TaskHandler 的 @TaskRetryable 注解中解析重试参数，
 * 与 TaskInstance 中的配置合并，返回最终生效的重试参数。
 *
 * <p>优先级: @TaskRetryable 注解 > TaskInstance 配置 > 默认值
 */
@Slf4j
public class RetryStrategyResolver {

    /**
     * 默认最大延迟上限，5 分钟
     */
    private static final long DEFAULT_MAX_DELAY_MS = 300_000L;

    /**
     * 默认最大重试次数（不含首次执行）
     */
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    /**
     * 默认重试间隔，1 秒
     */
    private static final long DEFAULT_RETRY_INTERVAL_MS = 1000L;

    private RetryStrategyResolver() {
    }

    /**
     * 解析 Handler 注解和 TaskInstance 配置，返回最终生效的重试参数
     *
     * <p>优先级规则:
     * <ol>
     *   <li>@TaskRetryable 注解中非默认值（>-1 或非 null）</li>
     *   <li>TaskInstance 中的配置</li>
     *   <li>框架默认值</li>
     * </ol>
     *
     * @param handler 任务处理器实例
     * @param task    当前任务实例
     * @return 合并后的重试参数
     */
    public static ResolvedRetryConfig resolve(TaskHandler handler, TaskInstance task) {
        return resolve(handler, task, new RetryProperties());
    }

    /**
     * 解析 Handler 注解、TaskInstance 配置和全局重试配置。
     */
    public static ResolvedRetryConfig resolve(TaskHandler handler, TaskInstance task,
                                              RetryProperties retryProperties) {
        TaskRetryable annotation = handler.getClass().getAnnotation(TaskRetryable.class);
        RetryProperties properties = retryProperties != null ? retryProperties : new RetryProperties();

        int maxRetryCount = resolveMaxRetryCount(annotation, task);
        RetryStrategyType strategy = resolveStrategy(annotation, task);
        long retryIntervalMs = resolveRetryIntervalMs(annotation, task);
        long maxDelayMs = resolveMaxDelayMs(annotation, properties);
        long minDelayMs = resolveMinDelayMs(properties, maxDelayMs);

        return new ResolvedRetryConfig(maxRetryCount, strategy, retryIntervalMs, minDelayMs, maxDelayMs);
    }

    private static int resolveMaxRetryCount(TaskRetryable annotation, TaskInstance task) {
        if (annotation != null && annotation.maxRetryCount() >= 0) {
            return annotation.maxRetryCount();
        }
        if (task.getMaxRetryCount() != null && task.getMaxRetryCount() >= 0) {
            return task.getMaxRetryCount();
        }
        return DEFAULT_MAX_RETRY_COUNT;
    }

    private static RetryStrategyType resolveStrategy(TaskRetryable annotation, TaskInstance task) {
        if (annotation != null && annotation.strategy() != null) {
            return annotation.strategy();
        }
        if (task.getRetryStrategy() != null) {
            return task.getRetryStrategy();
        }
        return RetryStrategyType.EXPONENTIAL;
    }

    private static long resolveRetryIntervalMs(TaskRetryable annotation, TaskInstance task) {
        if (annotation != null && annotation.retryIntervalMs() > 0) {
            return annotation.retryIntervalMs();
        }
        if (task.getRetryIntervalMs() != null && task.getRetryIntervalMs() > 0) {
            return task.getRetryIntervalMs();
        }
        return DEFAULT_RETRY_INTERVAL_MS;
    }

    private static long resolveMaxDelayMs(TaskRetryable annotation, RetryProperties retryProperties) {
        if (annotation != null && annotation.maxDelayMs() > 0) {
            return annotation.maxDelayMs();
        }
        if (retryProperties.getMaxDelayMs() > 0) {
            return retryProperties.getMaxDelayMs();
        }
        return DEFAULT_MAX_DELAY_MS;
    }

    private static long resolveMinDelayMs(RetryProperties retryProperties, long maxDelayMs) {
        long minDelayMs = Math.max(0L, retryProperties.getMinDelayMs());
        return Math.min(minDelayMs, maxDelayMs);
    }

    /**
     * 解析后的重试参数
     */
    public static class ResolvedRetryConfig {
        private final int maxRetryCount;
        private final RetryStrategyType strategy;
        private final long retryIntervalMs;
        private final long minDelayMs;
        private final long maxDelayMs;

        public ResolvedRetryConfig(int maxRetryCount, RetryStrategyType strategy,
                                   long retryIntervalMs, long minDelayMs, long maxDelayMs) {
            this.maxRetryCount = maxRetryCount;
            this.strategy = strategy;
            this.retryIntervalMs = retryIntervalMs;
            this.minDelayMs = minDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public RetryStrategyType getStrategy() {
            return strategy;
        }

        public long getRetryIntervalMs() {
            return retryIntervalMs;
        }

        public long getMinDelayMs() {
            return minDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }
    }
}
