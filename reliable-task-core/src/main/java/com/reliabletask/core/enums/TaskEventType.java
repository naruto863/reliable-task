package com.reliabletask.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态事件类型。
 */
@Getter
@AllArgsConstructor
public enum TaskEventType {

    /**
     * 新任务已投递，进入 PENDING。
     */
    SUBMITTED("任务已投递"),

    /**
     * Worker 抢占任务成功，进入 RUNNING。
     */
    STARTED("任务开始执行"),

    /**
     * 任务执行成功，进入 SUCCESS。
     */
    SUCCEEDED("任务执行成功"),

    /**
     * 任务失败后已安排重试，进入 RETRYING。
     */
    RETRY_SCHEDULED("任务已安排重试"),

    /**
     * 任务进入 DEAD。
     */
    DEAD("任务进入死亡状态"),

    /**
     * 任务被人工取消，进入 CANCELLED。
     */
    CANCELLED("任务已取消"),

    /**
     * DEAD/CANCELLED 任务被人工重新入队，进入 PENDING。
     */
    REQUEUED("任务已重新入队"),

    /**
     * 超时 RUNNING 任务被恢复为 PENDING。
     */
    RECOVERED("任务已恢复");

    private final String description;
}
