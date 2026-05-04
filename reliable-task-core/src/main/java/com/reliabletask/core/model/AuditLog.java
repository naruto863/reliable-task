package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理操作审计日志领域模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

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
