package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Worker 心跳表 Entity。
 *
 * <p>该表记录运行中 Worker 的观测状态，用于 Admin 展示和容量诊断。
 * 它不是任务锁来源，任务是否可恢复仍以 reliable_task.lock_expire_at 和状态条件为准。
 */
@Data
@TableName("reliable_task_worker")
public class ReliableTaskWorkerEntity {

    /**
     * Worker 进程级标识，通常为 hostname:shortUuid，应用重启后会变化。
     */
    @TableId
    private String workerId;

    private String appName;

    private String hostName;

    private String ipAddress;

    private String processId;

    /**
     * Worker 状态码：0-OFFLINE、1-ONLINE、2-STALE。
     */
    private Integer status;

    private Integer runningTaskCount;

    private Integer maxConcurrency;

    private Integer availableCapacity;

    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime startTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
