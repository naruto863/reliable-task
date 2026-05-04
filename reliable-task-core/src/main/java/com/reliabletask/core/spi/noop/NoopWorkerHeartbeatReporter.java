package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.WorkerHeartbeat;
import com.reliabletask.core.spi.WorkerHeartbeatReporter;

/**
 * 默认 Worker 心跳实现：忽略心跳事件。
 */
public class NoopWorkerHeartbeatReporter implements WorkerHeartbeatReporter {

    @Override
    public void report(WorkerHeartbeat heartbeat) {
        // no-op
    }
}
