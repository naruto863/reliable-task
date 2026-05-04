package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Worker 心跳表 Entity。
 */
@Data
@TableName("reliable_task_worker")
public class ReliableTaskWorkerEntity {

    @TableId
    private String workerId;

    private String appName;

    private String hostName;

    private String ipAddress;

    private String processId;

    private Integer status;

    private Integer runningTaskCount;

    private Integer maxConcurrency;

    private Integer availableCapacity;

    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime startTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
