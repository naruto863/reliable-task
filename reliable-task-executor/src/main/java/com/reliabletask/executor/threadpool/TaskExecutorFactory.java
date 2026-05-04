package com.reliabletask.executor.threadpool;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 任务执行线程池工厂
 *
 * <p>管理按 taskType 隔离的线程池:
 * <ul>
 *   <li>启动时创建 default 线程池，处理未单独配置的任务类型</li>
 *   <li>为 ThreadPoolProperties.pools 中配置的 taskType 创建独立线程池</li>
 *   <li>应用关闭时优雅关闭所有线程池</li>
 * </ul>
 *
 * <p>拒绝策略: CallerRunsPolicy（调用者线程执行，天然背压，防止任务丢失）
 */
@Slf4j
@Component
public class TaskExecutorFactory {

    private final ThreadPoolProperties properties;

    /**
     * default 线程池，处理未单独配置的 taskType
     */
    private final ThreadPoolExecutor defaultPool;

    /**
     * 按 taskType 隔离的独立线程池
     */
    private final Map<String, ThreadPoolExecutor> customPools;

    public TaskExecutorFactory(ThreadPoolProperties properties) {
        this.properties = properties;
        this.customPools = new ConcurrentHashMap<>();

        this.defaultPool = createPool(
                "default",
                properties.getDefaultCoreSize(),
                properties.getDefaultMaxSize(),
                properties.getDefaultQueueCapacity()
        );

        for (Map.Entry<String, ThreadPoolProperties.PoolConfig> entry : properties.getPools().entrySet()) {
            String taskType = entry.getKey();
            ThreadPoolProperties.PoolConfig config = entry.getValue();
            customPools.put(taskType, createPool(
                    taskType,
                    config.getCoreSize(),
                    config.getMaxSize(),
                    config.getQueueCapacity()
            ));
        }

        log.info("TaskExecutorFactory initialized: defaultPool={}, customPools={}",
                defaultPool, customPools.keySet());
    }

    /**
     * 获取指定 taskType 的线程池
     *
     * <p>优先返回独立线程池，未配置时返回 default 线程池。
     *
     * @param taskType 任务类型
     * @return 对应的线程池
     */
    public ExecutorService getExecutor(String taskType) {
        ExecutorService pool = customPools.get(taskType);
        return pool != null ? pool : defaultPool;
    }

    /**
     * 获取当前所有执行线程池剩余可接收任务容量。
     *
     * <p>容量由剩余队列空间和可创建线程数共同组成，用于 Worker 拉取前背压。
     */
    public int getAvailableCapacity() {
        int capacity = availableCapacity(defaultPool);
        for (ThreadPoolExecutor pool : customPools.values()) {
            capacity += availableCapacity(pool);
        }
        return Math.max(capacity, 0);
    }

    public int getMaxCapacity() {
        int capacity = maxCapacity(defaultPool);
        for (ThreadPoolExecutor pool : customPools.values()) {
            capacity += maxCapacity(pool);
        }
        return Math.max(capacity, 0);
    }

    private int availableCapacity(ThreadPoolExecutor pool) {
        int remainingQueueCapacity = pool.getQueue().remainingCapacity();
        int remainingThreadCapacity = pool.getMaximumPoolSize() - pool.getActiveCount();
        return remainingQueueCapacity + Math.max(remainingThreadCapacity, 0);
    }

    private int maxCapacity(ThreadPoolExecutor pool) {
        return pool.getMaximumPoolSize() + pool.getQueue().remainingCapacity() + pool.getQueue().size();
    }

    /**
     * 创建线程池
     *
     * @param name          线程池名称（用于日志和线程命名）
     * @param coreSize      核心线程数
     * @param maxSize       最大线程数
     * @param queueCapacity 队列容量
     * @return 配置好的线程池
     */
    private ThreadPoolExecutor createPool(String name, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                properties.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new TaskThreadFactory(name),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        pool.allowCoreThreadTimeOut(true);
        log.info("Created thread pool: name={}, coreSize={}, maxSize={}, queueCapacity={}",
                name, coreSize, maxSize, queueCapacity);
        return pool;
    }

    /**
     * 应用关闭时优雅关闭所有线程池
     *
     * <p>先 shutdown，等待 30 秒，如果未完成则 shutdownNow。
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all thread pools...");

        shutdownPool(defaultPool, "default");
        for (Map.Entry<String, ThreadPoolExecutor> entry : customPools.entrySet()) {
            shutdownPool(entry.getValue(), entry.getKey());
        }

        log.info("All thread pools shut down");
    }

    private void shutdownPool(ThreadPoolExecutor pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Thread pool '{}' did not terminate in 30s, forcing shutdown", name);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for thread pool '{}' to terminate", name);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 线程工厂，为线程设置可读名称
     */
    private static class TaskThreadFactory implements ThreadFactory {
        private final String poolName;
        private final ThreadGroup group;
        private int threadCount;

        TaskThreadFactory(String poolName) {
            this.poolName = poolName;
            this.group = Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    "reliable-task-" + poolName + "-" + ++threadCount);
            t.setDaemon(false);
            return t;
        }
    }
}
