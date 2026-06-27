package com.reliabletask.executor.threadpool;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程池配置属性
 *
 * <p>支持全局默认配置和按 taskType 的独立配置。
 * 未配置的 taskType 使用 default 线程池。
 *
 * <p>这些配置只影响本 JVM 的执行并发和排队能力，不改变数据库侧任务抢占条件。
 * 与 Worker 背压一起使用时，拉取数量会尽量贴合当前执行器可用容量。
 */
@Data
public class ThreadPoolProperties {

    /**
     * 执行器模式。
     *
     * <p>默认使用传统平台线程池；virtual 模式使用 JDK 21 虚拟线程，
     * 并通过 maxSize 控制同一执行器的最大并发任务数。
     */
    private ExecutionMode mode = ExecutionMode.PLATFORM;

    /**
     * 默认核心线程数
     */
    private int defaultCoreSize = 4;

    /**
     * 默认最大线程数
     */
    private int defaultMaxSize = 16;

    /**
     * 默认队列容量
     */
    private int defaultQueueCapacity = 100;

    /**
     * 线程空闲存活时间（秒），超过 coreSize 的线程
     */
    private int keepAliveSeconds = 60;

    /**
     * 按 taskType 的独立线程池配置
     *
     * <p>Key 为 taskType，Value 为该类型的线程池配置。
     * 未在 map 中配置的 taskType 使用 default 线程池。
     */
    private Map<String, PoolConfig> pools = new HashMap<>();

    public enum ExecutionMode {
        PLATFORM,
        VIRTUAL
    }

    /**
     * 单个 taskType 的线程池配置
     *
     * <p>未显式配置的字段会在 TaskExecutorFactory 中回退到全局默认值，
     * 因此这里保持简单数据对象，不在 setter 中做归一化。
     */
    @Data
    public static class PoolConfig {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;

        public PoolConfig() {
        }

        public PoolConfig(int coreSize, int maxSize, int queueCapacity) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }
    }
}
