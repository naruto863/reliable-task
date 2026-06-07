package com.reliabletask.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * ReliableTask 根配置属性类
 *
 * <p>前缀: reliable-task
 * <p>所有配置项均可通过 application.yml 覆盖，默认值保证开箱即用。
 *
 * <p>使用示例:
 * <pre>
 * reliable-task:
 *   enabled: true
 *   worker:
 *     enabled: true
 *     poll-interval-ms: 5000
 *     batch-size: 10
 *     max-batch-size: 1000
 *     lock-ttl-seconds: 300
 *     backpressure:
 *       enabled: false
 *       min-fetch-size: 1
 *       max-fetch-size: 10
 *   recovery:
 *     enabled: true
 *     interval-ms: 30000
 *     timeout-seconds: 300
 *     max-reset-per-scan: 100
 *   metrics:
 *     enabled: false
 *   alert:
 *     enabled: false
 *     pending-threshold: 0
 *     failure-rate-threshold: 1.0
 *     window-seconds: 300
 *   idempotency:
 *     strategy: STRICT_UNIQUE
 *   retry:
 *     exponential-multiplier: 2.0
 *     jitter-ratio: 0.0
 *     min-delay-ms: 0
 *     max-delay-ms: 300000
 *   # serializer.type is reserved; override TaskPayloadSerializer Bean to customize serialization.
 *   executor:
 *     default-core-size: 4
 *     default-max-size: 16
 *     default-queue-capacity: 100
 *     keep-alive-seconds: 60
 *     pools:
 *       SEND_EMAIL:
 *         core-size: 8
 *         max-size: 16
 *         queue-capacity: 200
 *   # store.table-prefix is reserved; current MyBatis implementation uses fixed table names.
 *   admin:
 *     enabled: true
 *     write-enabled: false
 *     max-page-size: 200
 *     max-batch-limit: 1000
 *     # port/context-path are reserved; Admin APIs are served by the application server under /api/reliable-task.
 *     audit:
 *       enabled: false
 *     auth:
 *       enabled: false
 *     batch:
 *       enabled: false
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "reliable-task")
public class ReliableTaskProperties {

    /**
     * 是否启用 ReliableTask，默认 true
     */
    private boolean enabled = true;

    /**
     * Worker 调度配置
     */
    private Worker worker = new Worker();

    /**
     * 补偿恢复配置
     */
    private Recovery recovery = new Recovery();

    /**
     * 线程池执行器配置
     */
    private Executor executor = new Executor();

    /**
     * 指标配置
     */
    private Metrics metrics = new Metrics();

    /**
     * 告警配置
     */
    private Alert alert = new Alert();

    /**
     * 幂等配置
     */
    private Idempotency idempotency = new Idempotency();

    /**
     * 重试配置
     */
    private Retry retry = new Retry();

    /**
     * payload 序列化配置
     */
    private Serializer serializer = new Serializer();

    /**
     * 存储层配置
     */
    private Store store = new Store();

    /**
     * 管理后台配置
     */
    private Admin admin = new Admin();

    /**
     * Worker 调度配置
     */
    @Data
    public static class Worker {
        /**
         * 是否启用 Worker，默认 true
         */
        private boolean enabled = true;

        /**
         * 拉取间隔，单位毫秒，默认 5000
         */
        private long pollIntervalMs = 5000L;

        /**
         * 单次拉取任务数量，默认 10
         */
        private int batchSize = 10;

        /**
         * 单次拉取任务数量上限，默认 1000
         */
        private int maxBatchSize = 1000;

        /**
         * Worker 抢占任务后的初始锁 TTL，单位秒，默认 300
         */
        private long lockTtlSeconds = 300L;

        /**
         * Worker 背压配置
         */
        private Backpressure backpressure = new Backpressure();

        /**
         * Worker 心跳配置
         */
        private Heartbeat heartbeat = new Heartbeat();

        @Data
        public static class Backpressure {
            /**
             * 是否启用 Worker 背压，默认 false 保持 V1.5 行为
             */
            private boolean enabled = false;

            /**
             * 背压启用时的最小拉取数量，默认 1
             */
            private int minFetchSize = 1;

            /**
             * 背压启用时的最大拉取数量，默认 10
             */
            private int maxFetchSize = 10;
        }

        @Data
        public static class Heartbeat {
            /**
             * 是否启用 Worker 心跳，默认 false 保持 V1.5 行为
             */
            private boolean enabled = false;

            /**
             * 心跳和续约间隔，单位毫秒，默认 10000
             */
            private long intervalMs = 10000L;

            /**
             * 任务锁续约 TTL，单位秒，默认 300
             */
            private long lockRenewalTtlSeconds = 300L;

            /**
             * Worker 失联阈值，单位秒，默认 60
             */
            private long staleWorkerThresholdSeconds = 60L;
        }
    }

    /**
     * 补偿恢复配置
     */
    @Data
    public static class Recovery {
        /**
         * 是否启用补偿扫描，默认 true
         */
        private boolean enabled = true;

        /**
         * 扫描间隔，单位毫秒，默认 30000
         */
        private long intervalMs = 30000L;

        /**
         * 兼容保留的任务超时阈值，单位秒，默认 300（5 分钟）。
         *
         * <p>当前 Recovery 以 lockExpireAt <= now 为准，不再在锁过期后额外等待本字段。
         */
        private long timeoutSeconds = 300L;

        /**
         * 单次扫描最大重置数量，默认 100
         */
        private int maxResetPerScan = 100;
    }

    /**
     * 线程池执行器配置
     */
    @Data
    public static class Executor {
        /**
         * 默认核心线程数，默认 4
         */
        private int defaultCoreSize = 4;

        /**
         * 默认最大线程数，默认 16
         */
        private int defaultMaxSize = 16;

        /**
         * 默认队列容量，默认 100
         */
        private int defaultQueueCapacity = 100;

        /**
         * 线程空闲存活时间（秒），默认 60
         */
        private int keepAliveSeconds = 60;

        /**
         * 按 taskType 的独立线程池配置
         */
        private Map<String, PoolConfig> pools = new HashMap<>();

        @Data
        public static class PoolConfig {
            private int coreSize;
            private int maxSize;
            private int queueCapacity;
        }
    }

    /**
     * 指标配置
     */
    @Data
    public static class Metrics {
        /**
         * 是否启用 Micrometer 指标，默认 false
         */
        private boolean enabled = false;

        /**
         * 是否在执行类指标中包含 workerId tag，默认关闭以避免高基数。
         */
        private boolean includeWorkerIdTag = false;

        /**
         * 任务统计 Gauge 快照缓存时间，单位毫秒，默认 5000。
         */
        private long statsCacheTtlMs = 5000L;
    }

    /**
     * 告警配置
     */
    @Data
    public static class Alert {
        /**
         * 是否启用告警闭环，默认 false
         */
        private boolean enabled = false;

        /**
         * pending 积压阈值，0 表示不触发积压告警
         */
        private long pendingThreshold = 0L;

        /**
         * 窗口失败率阈值，取值 0-1
         */
        private double failureRateThreshold = 1.0D;

        /**
         * 失败率统计窗口，单位秒
         */
        private long windowSeconds = 300L;

        /**
         * 积压扫描间隔，单位毫秒
         */
        private long scanIntervalMs = 30000L;
    }

    /**
     * 幂等配置
     */
    @Data
    public static class Idempotency {
        /**
         * 默认幂等策略，默认 STRICT_UNIQUE 保持 V1.5 行为
         */
        private String strategy = "STRICT_UNIQUE";
    }

    /**
     * 重试策略配置
     */
    @Data
    public static class Retry {
        /**
         * 指数退避增长倍数，默认 2.0
         */
        private double exponentialMultiplier = 2.0D;

        /**
         * 指数退避抖动比例，0 表示关闭抖动
         */
        private double jitterRatio = 0.0D;

        /**
         * 最小重试延迟，单位毫秒，默认 0
         */
        private long minDelayMs = 0L;

        /**
         * 最大重试延迟，单位毫秒，默认 300000
         */
        private long maxDelayMs = 300_000L;
    }

    /**
     * payload 序列化配置
     */
    @Data
    public static class Serializer {
        /**
         * 保留配置。当前默认注册 JacksonTaskPayloadSerializer；
         * 如需自定义序列化，请覆盖 TaskPayloadSerializer Bean。
         */
        private String type = "JACKSON";
    }

    /**
     * 存储层配置
     */
    @Data
    public static class Store {
        /**
         * 保留配置。当前 MyBatis Mapper 和 schema 使用固定表名，
         * 本字段暂不参与表名解析。
         */
        private String tablePrefix = "";
    }

    /**
     * 管理后台配置
     */
    @Data
    public static class Admin {
        /**
         * 是否启用管理后台，默认 false
         */
        private boolean enabled = false;

        /**
         * 是否启用 Admin 写操作，默认 false
         */
        private boolean writeEnabled = false;

        /**
         * 保留配置。当前不创建独立管理端口，Admin API 使用业务应用 server.port。
         */
        private int port = 9090;

        /**
         * 保留配置。当前 Admin Controller 固定映射在 /api/reliable-task。
         */
        private String contextPath = "/reliable-task";

        /**
         * Admin 分页大小上限，默认 200
         */
        private int maxPageSize = 200;

        /**
         * Admin 批量操作 limit 上限，默认 1000
         */
        private int maxBatchLimit = 1000;

        /**
         * 操作审计配置
         */
        private Audit audit = new Audit();

        /**
         * 权限接入配置
         */
        private Auth auth = new Auth();

        /**
         * 批量运维配置
         */
        private Batch batch = new Batch();

        @Data
        public static class Audit {
            /**
             * 是否启用 Admin 操作审计，默认 false
             */
            private boolean enabled = false;
        }

        @Data
        public static class Auth {
            /**
             * 是否启用 Admin 权限检查，默认 true
             */
            private boolean enabled = true;
        }

        @Data
        public static class Batch {
            /**
             * 是否启用 Admin 批量运维，默认 false
             */
            private boolean enabled = false;
        }
    }
}
