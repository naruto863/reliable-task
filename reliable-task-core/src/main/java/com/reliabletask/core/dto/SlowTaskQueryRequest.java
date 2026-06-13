package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 慢任务查询请求。
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
