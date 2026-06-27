package com.reliabletask.executor.worker;

import lombok.Data;

/**
 * Worker 调度配置属性
 *
 * <p>控制 Worker 定时拉取任务的开关、间隔和批量大小。
 *
 * <p>该对象是 executor 内部运行时配置，Spring Boot starter 会从
 * reliable-task.worker.* 绑定后再转换为它。这里的默认值兼顾向后兼容，
 * 因此背压、心跳和续约默认关闭，避免升级后改变旧应用的调度节奏。
 */
@Data
public class WorkerProperties {

    /**
     * 是否启用 Worker，默认 true
     */
    private boolean enabled = true;

    /**
     * 拉取间隔，单位毫秒，默认 5000（5 秒）
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
     * 任务初始锁 TTL，单位秒，默认 300
     */
    private long lockTtlSeconds = 300L;

    /**
     * 是否启用拉取前背压，默认 false 保持 V1.5 行为
     *
     * <p>开启后 Worker 会根据执行器剩余容量调整本次拉取数量，减少本地队列堆积。
     */
    private boolean backpressureEnabled = false;

    /**
     * 背压启用时的最小拉取数量，默认 1
     */
    private int backpressureMinFetchSize = 1;

    /**
     * 背压启用时的最大拉取数量，默认 10
     */
    private int backpressureMaxFetchSize = 10;

    /**
     * 是否启用 Worker 心跳和任务续约，默认 false 保持 V1.5 行为
     *
     * <p>开启后运行中任务会周期续约，适合长耗时 Handler；关闭时仍依赖初始锁 TTL
     * 和恢复扫描兜底，适合短任务和兼容旧版本。
     */
    private boolean heartbeatEnabled = false;

    /**
     * 心跳和续约间隔，单位毫秒，默认 10000
     */
    private long heartbeatIntervalMs = 10000L;

    /**
     * 任务锁续约 TTL，单位秒，默认 300
     */
    private long lockRenewalTtlSeconds = 300L;

    /**
     * Worker 失联阈值，单位秒，默认 60
     *
     * <p>该阈值用于运维视图判断 Worker 是否 STALE，不直接决定任务锁是否过期；
     * 任务恢复仍以任务行上的 lockExpireAt 为准。
     */
    private long staleWorkerThresholdSeconds = 60L;
}
