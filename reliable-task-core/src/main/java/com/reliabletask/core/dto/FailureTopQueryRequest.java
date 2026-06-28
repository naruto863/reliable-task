package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 失败 Top 聚合查询请求。
 *
 * <p>用于 Admin 故障分析页的只读聚合查询。groupBy 由 Controller/Store 白名单解释，
 * 不能直接拼接成 SQL 字段，避免前端输入影响排序或聚合字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureTopQueryRequest {

    /**
     * 聚合维度，例如 taskType、errorCode 或 taskType,errorCode。
     */
    private String groupBy;

    private String taskType;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;

    private Integer limit;
}
