package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最近失败查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskFailureQueryRequest {

    private String taskType;

    private String errorCode;

    private LocalDateTime createTimeStart;

    private LocalDateTime createTimeEnd;

    private Integer limit;
}
