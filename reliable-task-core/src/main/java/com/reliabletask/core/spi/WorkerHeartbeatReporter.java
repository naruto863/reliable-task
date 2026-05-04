package com.reliabletask.core.spi;

import com.reliabletask.core.model.WorkerHeartbeat;

/**
 * Worker 心跳上报 SPI。
 */
public interface WorkerHeartbeatReporter {

    /**
     * 上报 Worker 心跳。
     *
     * @param heartbeat Worker 心跳
     */
    void report(WorkerHeartbeat heartbeat);
}
