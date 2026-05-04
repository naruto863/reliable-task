package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Worker 心跳领域模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerHeartbeat {

    private String workerId;

    private String appName;

    private String hostName;

    private String ipAddress;

    private String processId;

    private String status;

    private int runningTaskCount;

    private int maxConcurrency;

    private int availableCapacity;

    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime startTime;
}
