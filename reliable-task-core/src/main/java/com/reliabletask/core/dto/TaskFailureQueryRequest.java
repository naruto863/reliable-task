package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最近失败查询请求。
 *
 * <p>服务 Admin 最近失败列表，查询的是执行日志表中的失败记录，而不是任务主表的当前状态。
 * 因此同一个任务可能出现多条失败记录，后续重试成功也不会删除历史失败事实。
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
