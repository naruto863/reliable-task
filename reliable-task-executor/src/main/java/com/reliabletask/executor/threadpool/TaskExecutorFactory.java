package com.reliabletask.executor.threadpool;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
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
 * <p>platform 模式拒绝策略: CallerRunsPolicy（调用者线程执行，天然背压，防止任务丢失）。
 * virtual 模式使用 JDK 21 虚拟线程，并通过有界信号量保留 maxSize 并发上限语义。
 */
@Slf4j
@Component
public class TaskExecutorFactory {

    private final ThreadPoolProperties properties;

    /**
     * default 线程池，处理未单独配置的 taskType
     */
    private final ManagedExecutor defaultPool;

    /**
     * 按 taskType 隔离的独立线程池
     */
    private final Map<String, ManagedExecutor> customPools;

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
                defaultPool.executor(), customPools.keySet());
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
        ManagedExecutor pool = customPools.get(taskType);
        return pool != null ? pool.executor() : defaultPool.executor();
    }

    /**
     * 获取当前所有执行线程池剩余可接收任务容量。
     *
     * <p>容量由剩余队列空间和可创建线程数共同组成，用于 Worker 拉取前背压。
     */
    public int getAvailableCapacity() {
        int capacity = defaultPool.availableCapacity();
        for (ManagedExecutor pool : customPools.values()) {
            capacity += pool.availableCapacity();
        }
        return Math.max(capacity, 0);
    }

    public int getMaxCapacity() {
        int capacity = defaultPool.maxCapacity();
        for (ManagedExecutor pool : customPools.values()) {
            capacity += pool.maxCapacity();
        }
        return Math.max(capacity, 0);
    }

    /**
     * 创建执行器
     *
     * @param name          线程池名称（用于日志和线程命名）
     * @param coreSize      核心线程数
     * @param maxSize       最大线程数
     * @param queueCapacity 队列容量
     * @return 配置好的执行器
     */
    private ManagedExecutor createPool(String name, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolProperties.ExecutionMode mode =
                properties.getMode() != null ? properties.getMode() : ThreadPoolProperties.ExecutionMode.PLATFORM;
        if (mode == ThreadPoolProperties.ExecutionMode.VIRTUAL) {
            // 虚拟线程模式不使用队列容量，但仍保留 maxSize 作为业务并发上限。
            return createVirtualPool(name, maxSize);
        }
        return createPlatformPool(name, coreSize, maxSize, queueCapacity);
    }

    private ManagedExecutor createPlatformPool(String name, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                properties.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new TaskThreadFactory(name),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        // CallerRunsPolicy 让 Worker 调度线程在饱和时自己执行任务，形成自然背压而不是直接丢任务。
        pool.allowCoreThreadTimeOut(true);
        log.info("Created thread pool: name={}, coreSize={}, maxSize={}, queueCapacity={}",
                name, coreSize, maxSize, queueCapacity);
        return new PlatformManagedExecutor(pool);
    }

    private ManagedExecutor createVirtualPool(String name, int maxSize) {
        BoundedVirtualExecutor executor = new BoundedVirtualExecutor(name, maxSize);
        log.info("Created virtual thread executor: name={}, maxConcurrency={}", name, maxSize);
        return executor;
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
        for (Map.Entry<String, ManagedExecutor> entry : customPools.entrySet()) {
            shutdownPool(entry.getValue(), entry.getKey());
        }

        log.info("All thread pools shut down");
    }

    private void shutdownPool(ManagedExecutor pool, String name) {
        ExecutorService executor = pool.executor();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Thread pool '{}' did not terminate in 30s, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for thread pool '{}' to terminate", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private interface ManagedExecutor {
        ExecutorService executor();

        int availableCapacity();

        int maxCapacity();
    }

    private static class PlatformManagedExecutor implements ManagedExecutor {
        private final ThreadPoolExecutor executor;

        private PlatformManagedExecutor(ThreadPoolExecutor executor) {
            this.executor = executor;
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }

        @Override
        public int availableCapacity() {
            // 背压估算同时看队列余量和可新增执行线程；这是 Worker 本轮最多应继续拉取的任务量。
            int remainingQueueCapacity = executor.getQueue().remainingCapacity();
            int remainingThreadCapacity = executor.getMaximumPoolSize() - executor.getActiveCount();
            return remainingQueueCapacity + Math.max(remainingThreadCapacity, 0);
        }

        @Override
        public int maxCapacity() {
            // 最大容量用于心跳展示，包含正在运行、排队中和仍可接收的任务槽位。
            return executor.getMaximumPoolSize() + executor.getQueue().remainingCapacity() + executor.getQueue().size();
        }
    }

    private static class BoundedVirtualExecutor extends AbstractExecutorService implements ManagedExecutor {
        private final ExecutorService delegate;
        private final Semaphore permits;
        private final int maxConcurrency;

        private BoundedVirtualExecutor(String name, int maxConcurrency) {
            if (maxConcurrency <= 0) {
                throw new IllegalArgumentException("Virtual executor maxSize must be positive");
            }
            this.maxConcurrency = maxConcurrency;
            // JDK 虚拟线程本身几乎不限制数量，这里用 Semaphore 显式恢复“线程池最大并发”的业务语义。
            this.permits = new Semaphore(maxConcurrency);
            ThreadFactory threadFactory = Thread.ofVirtual()
                    .name("reliable-task-" + name + "-virtual-", 0)
                    .factory();
            this.delegate = Executors.newThreadPerTaskExecutor(threadFactory);
        }

        @Override
        public ExecutorService executor() {
            return this;
        }

        @Override
        public int availableCapacity() {
            return permits.availablePermits();
        }

        @Override
        public int maxCapacity() {
            return maxConcurrency;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            boolean acquired = false;
            try {
                // acquire 会阻塞提交方，从而把虚拟线程模式也纳入 Worker 背压闭环。
                permits.acquire();
                acquired = true;
                delegate.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        permits.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for virtual executor capacity", e);
            } catch (RuntimeException e) {
                if (acquired) {
                    permits.release();
                }
                throw e;
            }
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
