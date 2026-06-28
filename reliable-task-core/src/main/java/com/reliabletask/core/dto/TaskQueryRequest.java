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
 *
 * <p>该 DTO 承载查询条件，不负责权限和写保护。分页大小在这里做基础归一化，
 * Admin 运维查询的默认时间窗口与最大窗口由 AdminQueryGuard 单独处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueryRequest {

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 200;

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
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页大小
     */
    @Builder.Default
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 归一化页码，防止外部请求传入 0 或负数。
     */
    public int normalizedPageNum() {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 归一化分页大小，防止外部请求传入 0、负数或过大值。
     */
    public int normalizedPageSize(int maxPageSize) {
        int effectiveMaxPageSize = maxPageSize > 0 ? maxPageSize : DEFAULT_MAX_PAGE_SIZE;
        if (pageSize == null || pageSize <= 0) {
            return Math.min(DEFAULT_PAGE_SIZE, effectiveMaxPageSize);
        }
        return Math.min(pageSize, effectiveMaxPageSize);
    }

    /**
     * 获取 MyBatis-Plus 分页偏移量
     *
     * <p>该方法保留给旧调用方使用；新的分页查询优先直接使用 normalizedPageNum/normalizedPageSize
     * 构造 Page 对象，避免 maxPageSize 与默认值不一致。
     */
    public long getOffset() {
        return (long) (normalizedPageNum() - 1) * normalizedPageSize(DEFAULT_MAX_PAGE_SIZE);
    }
}
