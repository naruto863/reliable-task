package com.reliabletask.core.model;

import com.reliabletask.core.enums.RetryStrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务投递请求 DTO
 *
 * <p>业务方通过 TaskTemplate.submit() 投递任务时使用的请求对象。
 * 使用 Builder 模式构建，必填字段通过构造/Builder 强制，可选字段有默认值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmitRequest {

    /**
     * 任务类型（必填）
     * 用于路由到对应的 TaskHandler 实现
     */
    private String taskType;

    /**
     * 业务类型（必填）
     * 如 ORDER/USER/PAYMENT
     */
    private String bizType;

    /**
     * 业务唯一标识（必填）
     * 如订单号、用户 ID 等
     */
    private String bizId;

    /**
     * 任务参数（必填）
     * JSON 格式字符串。V1.5 兼容入口，字符串会原样入库。
     */
    private String payload;

    /**
     * 对象任务参数（可选）。
     *
     * <p>设置后由 TaskPayloadSerializer 在投递前序列化为入库字符串。
     * 如果同时设置 payload 和 payloadObject，优先使用 payloadObject。
     */
    private Object payloadObject;

    /**
     * 幂等策略名称（可选）。
     *
     * <p>为空时使用组件默认策略。
     */
    private String idempotencyStrategy;

    /**
     * 优先级（可选，默认 5）
     * 0-9，数字越小优先级越高
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 最大重试次数（可选，默认 3，不含首次执行）
     */
    @Builder.Default
    private Integer maxRetryCount = 3;

    /**
     * 重试策略（可选，默认 EXPONENTIAL）
     */
    @Builder.Default
    private RetryStrategyType retryStrategy = RetryStrategyType.EXPONENTIAL;

    /**
     * 基础重试间隔，单位毫秒（可选，默认 1000）
     */
    @Builder.Default
    private Long retryIntervalMs = 1000L;

    /**
     * 分片键（可选）
     */
    private String shardKey;

    /**
     * 租户标识（可选）
     */
    private String tenantId;
}
