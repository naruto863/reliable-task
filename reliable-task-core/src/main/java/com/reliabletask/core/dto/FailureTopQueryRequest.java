package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 失败 Top 聚合查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureTopQueryRequest {

    private String groupBy;

    private String taskType;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;

    private Integer limit;
}
