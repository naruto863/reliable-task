package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 操作审计表 Entity。
 */
@Data
@TableName("reliable_task_audit_log")
public class ReliableTaskAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;

    private String operator;

    private String targetType;

    private String targetId;

    private Long taskId;

    private Long batchOperationId;

    private String requestSummary;

    private String result;

    private String errorMsg;

    private String traceId;

    private LocalDateTime createTime;
}
