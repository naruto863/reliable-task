package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行租约。
 *
 * <p>Worker 成功抢占任务后，应把本对象沿执行链路传递给成功、重试、死信和恢复回写。
 * 存储实现可以使用 workerId、lockedAt、lockExpireAt 或 version 做 CAS 条件，防止旧 Worker 覆盖新状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLease {

    /**
     * 任务 ID。
     */
    private Long taskId;

    /**
     * 当前持有租约的 Worker ID。
     */
    private String workerId;

    /**
     * 本次抢占时写入的锁定时间。
     */
    private LocalDateTime lockedAt;

    /**
     * 本次租约过期时间。
     */
    private LocalDateTime lockExpireAt;

    /**
     * 抢占后任务版本号。
     */
    private Integer version;

    /**
     * 从任务实例提取执行租约。
     *
     * @param task 已抢占的任务实例
     * @return 执行租约；task 为 null 时返回 null
     */
    public static TaskExecutionLease from(TaskInstance task) {
        if (task == null) {
            return null;
        }
        return TaskExecutionLease.builder()
                .taskId(task.getId())
                .workerId(task.getWorkerId())
                .lockedAt(task.getLockedAt())
                .lockExpireAt(task.getLockExpireAt())
                .version(task.getVersion())
                .build();
    }
}
