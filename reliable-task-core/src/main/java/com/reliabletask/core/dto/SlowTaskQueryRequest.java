package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 慢任务查询请求。
 *
 * <p>durationMsGte 是运维分析阈值，只用于筛选执行日志中的慢记录；
 * 它不代表任务执行超时时间，也不会影响 Worker 的租约和恢复逻辑。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowTaskQueryRequest {

    private String taskType;

    private Long durationMsGte;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;

    private Integer limit;
}
