package com.reliabletask.executor.worker;

import lombok.Data;

/**
 * Worker 调度配置属性
 *
 * <p>控制 Worker 定时拉取任务的开关、间隔和批量大小。
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
     * 任务初始锁 TTL，单位秒，默认 300
     */
    private long lockTtlSeconds = 300L;

    /**
     * 是否启用拉取前背压，默认 false 保持 V1.5 行为
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
     */
    private long staleWorkerThresholdSeconds = 60L;
}
