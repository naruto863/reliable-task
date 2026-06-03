package com.reliabletask.core.vo;

import lombok.Data;

import java.util.Map;

/**
 * 任务统计视图对象
 *
 * <p>用于管理后台统计面板展示。
 */
@Data
public class TaskStatsVO {

    /**
     * 各状态任务数量
     * key: 状态码, value: 数量
     */
    private Map<Integer, Long> statusCount;

    /**
     * 总任务数
     */
    private long totalTasks;

    /**
     * 今日新增任务数
     */
    private long todayNewTasks;

    /**
     * 今日成功任务数
     */
    private long todaySuccessTasks;

    /**
     * 今日失败任务数
     */
    private long todayFailedTasks;

    /**
     * 当前待执行任务数（PENDING + RETRYING）
     */
    private long pendingTasks;

    /**
     * 需要人工干预的任务数（DEAD）
     */
    private long deadTasks;

    /**
     * 最老待执行任务年龄（秒，PENDING + RETRYING）
     */
    private long oldestPendingAgeSeconds;

    /**
     * 按 taskType 分组的任务数
     * key: taskType, value: 该类型的任务总数
     */
    private Map<String, Long> taskTypeStats;
}
