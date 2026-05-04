package com.reliabletask.core.dto;

import com.reliabletask.core.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务查询请求 DTO
 *
 * <p>用于管理后台任务列表的多条件筛选和分页查询。
 * 所有字段均为可选，不传则不过滤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueryRequest {

    /**
     * 任务状态筛选
     */
    private TaskStatus status;

    /**
     * 任务类型筛选
     */
    private String taskType;

    /**
     * 业务类型筛选
     */
    private String bizType;

    /**
     * 业务 ID 筛选（支持模糊匹配）
     */
    private String bizId;

    /**
     * 执行节点 ID 筛选
     */
    private String workerId;

    /**
     * 链路追踪 ID 筛选
     */
    private String traceId;

    /**
     * 租户标识筛选
     */
    private String tenantId;

    /**
     * 创建时间起始（含）
     */
    private LocalDateTime createTimeStart;

    /**
     * 创建时间结束（含）
     */
    private LocalDateTime createTimeEnd;

    /**
     * 页码，从 1 开始
     */
    @Builder.Default
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    @Builder.Default
    private Integer pageSize = 20;

    /**
     * 获取 MyBatis-Plus 分页偏移量
     */
    public long getOffset() {
        return (long) (pageNum - 1) * pageSize;
    }
}
