package com.reliabletask.executor.alert;

import lombok.Data;

/**
 * 告警配置。
 */
@Data
public class AlertProperties {

    private boolean enabled = false;

    private long pendingThreshold = 0L;

    private double failureRateThreshold = 1.0D;

    private long windowSeconds = 300L;

    private long scanIntervalMs = 30000L;
}
