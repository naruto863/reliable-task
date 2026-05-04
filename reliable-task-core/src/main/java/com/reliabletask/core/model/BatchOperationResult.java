package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量运维操作结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult {

    private Long batchOperationId;

    private int totalCount;

    private int successCount;

    private int failCount;

    private List<Long> failedTaskIds;

    private String failedSummary;

    private boolean dryRun;

    private boolean success;

    private String errorMsg;
}
