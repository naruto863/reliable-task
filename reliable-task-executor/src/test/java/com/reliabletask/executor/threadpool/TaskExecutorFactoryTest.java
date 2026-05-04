package com.reliabletask.executor.threadpool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskExecutorFactory 测试")
class TaskExecutorFactoryTest {

    private TaskExecutorFactory factory;

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.shutdown();
        }
    }

    @Test
    @DisplayName("getExecutor - 未配置 taskType 返回 default 线程池")
    void getExecutor_unconfiguredTask_returnsDefaultPool() {
        ThreadPoolProperties props = new ThreadPoolProperties();
        props.setDefaultCoreSize(2);
        factory = new TaskExecutorFactory(props);

        ExecutorService pool1 = factory.getExecutor("UNKNOWN_TYPE");
        ExecutorService pool2 = factory.getExecutor("ANOTHER_TYPE");

        assertSame(pool1, pool2);
    }

    @Test
    @DisplayName("getExecutor - 已配置 taskType 返回独立线程池")
    void getExecutor_configuredTask_returnsCustomPool() {
        ThreadPoolProperties props = new ThreadPoolProperties();
        props.setDefaultCoreSize(2);
        props.setPools(Map.of(
                "SEND_EMAIL", new ThreadPoolProperties.PoolConfig(4, 8, 50),
                "CREATE_ORDER", new ThreadPoolProperties.PoolConfig(8, 16, 100)
        ));
        factory = new TaskExecutorFactory(props);

        ExecutorService emailPool = factory.getExecutor("SEND_EMAIL");
        ExecutorService orderPool = factory.getExecutor("CREATE_ORDER");
        ExecutorService defaultPool = factory.getExecutor("UNKNOWN");

        assertNotNull(emailPool);
        assertNotNull(orderPool);
        assertNotNull(defaultPool);

        // 不同 taskType 的线程池应该不同
        assertNotSame(emailPool, orderPool);
        assertNotSame(emailPool, defaultPool);
        assertNotSame(orderPool, defaultPool);
    }

    @Test
    @DisplayName("getExecutor - 相同 taskType 返回相同实例")
    void getExecutor_sameTaskType_returnsSameInstance() {
        ThreadPoolProperties props = new ThreadPoolProperties();
        props.setPools(Map.of("TYPE_A", new ThreadPoolProperties.PoolConfig(2, 4, 20)));
        factory = new TaskExecutorFactory(props);

        ExecutorService pool1 = factory.getExecutor("TYPE_A");
        ExecutorService pool2 = factory.getExecutor("TYPE_A");

        assertSame(pool1, pool2);
    }

    @Test
    @DisplayName("构造函数 - 使用默认配置创建线程池")
    void constructor_usesDefaultConfig() {
        ThreadPoolProperties props = new ThreadPoolProperties();
        factory = new TaskExecutorFactory(props);

        ExecutorService pool = factory.getExecutor("ANY_TYPE");
        assertTrue(pool instanceof ThreadPoolExecutor);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) pool;

        assertEquals(4, tpe.getCorePoolSize());
        assertEquals(16, tpe.getMaximumPoolSize());
    }

    @Test
    @DisplayName("shutdown - 优雅关闭所有线程池")
    void shutdown_gracefullyShutsDownAllPools() {
        ThreadPoolProperties props = new ThreadPoolProperties();
        props.setPools(Map.of("TYPE_A", new ThreadPoolProperties.PoolConfig(1, 2, 10)));
        factory = new TaskExecutorFactory(props);

        factory.getExecutor("TYPE_A");
        factory.getExecutor("TYPE_B");

        factory.shutdown();

        ExecutorService poolA = factory.getExecutor("TYPE_A");
        assertTrue(poolA.isShutdown());
    }
}
