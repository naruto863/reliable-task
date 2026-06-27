package com.reliabletask.executor.alert;

import lombok.Data;

/**
 * 告警配置。
 *
 * <p>告警属于旁路能力，关闭时不会影响任务执行、状态流转和恢复扫描。
 * 只有启用后并注册 AlarmNotifier，死信、失败率和积压告警才会真正对外发送。
 */
@Data
public class AlertProperties {

    /**
     * 是否启用告警，默认关闭以避免 starter 接入后立刻产生外部副作用。
     */
    private boolean enabled = false;

    /**
     * pending/retrying 任务积压阈值，0 表示不触发积压告警。
     */
    private long pendingThreshold = 0L;

    /**
     * 执行失败率阈值，取值范围按 0.0~1.0 理解，默认 1.0 表示窗口内全部失败才告警。
     */
    private double failureRateThreshold = 1.0D;

    /**
     * 失败率统计窗口长度，单位秒，只对当前 JVM 内存窗口生效。
     */
    private long windowSeconds = 300L;

    /**
     * 积压扫描间隔，单位毫秒；实际定时表达式由 TaskAlertScheduler 的 @Scheduled 读取。
     */
    private long scanIntervalMs = 30000L;
}
