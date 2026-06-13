package com.reliabletask.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败 Top 聚合查询结果。
 */
@Data
public class FailureTopVO {

    private String taskType;

    private String errorCode;

    private Long failureCount;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;
}
