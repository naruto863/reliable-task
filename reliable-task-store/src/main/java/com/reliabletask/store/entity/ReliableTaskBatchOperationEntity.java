package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量运维操作表 Entity。
 *
 * <p>记录一次批量重试、取消等运维动作的选择条件和执行结果。
 * 该表用于审计和结果追踪，不作为后续任务调度的输入。
 */
@Data
@TableName("reliable_task_batch_operation")
public class ReliableTaskBatchOperationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;

    private String status;

    private String operator;

    private String taskType;

    private Integer taskStatus;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;

    private Integer operationLimit;

    private Integer dryRun;

    private String requestCondition;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private String failedSummary;

    private String errorMsg;

    private String traceId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime finishTime;
}
