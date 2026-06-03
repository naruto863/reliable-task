package com.reliabletask.executor.recovery;

import lombok.Data;

/**
 * 任务恢复补偿配置属性
 *
 * <p>控制补偿扫描定时任务的开关、间隔和超时阈值。
 * 用于发现并重置因 afterCommit 异常或 Worker 崩溃而卡住的 RUNNING 任务。
 */
@Data
public class RecoveryProperties {

    /**
     * 是否启用补偿扫描，默认 true
     */
    private boolean enabled = true;

    /**
     * 扫描间隔，单位毫秒，默认 30000（30 秒）
     */
    private long intervalMs = 30000L;

    /**
     * 兼容保留的任务超时阈值，单位秒，默认 300（5 分钟）
     *
     * <p>当前恢复语义以任务行的 lockExpireAt 为准：RUNNING 且 lockExpireAt <= now 的任务才会被重置。
     * 本字段保留给已有配置和后续宽限期设计，不再叠加到 lockExpireAt 之后形成二次等待。
     */
    private long timeoutSeconds = 300L;

    /**
     * 单次扫描最大重置数量，默认 100
     *
     * <p>防止一次性重置过多任务导致 Worker 瞬时压力。
     */
    private int maxResetPerScan = 100;
}
