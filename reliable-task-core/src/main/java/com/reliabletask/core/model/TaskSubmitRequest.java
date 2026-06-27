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
 *
 * <p>该对象只表达“提交意图”，不会直接决定最终入库状态。实际入库前还会经过模板层的
 * 参数校验、payload 序列化、幂等键生成、事务同步和 TaskStore 的唯一键兜底。
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
     * 这是为了让面向对象的新入口覆盖兼容字符串入口，避免同一次投递出现两份 payload
     * 时读者误以为二者会合并或同时入库。
     */
    private Object payloadObject;

    /**
     * 幂等策略名称（可选）。
     *
     * <p>为空时使用组件默认策略。
     */
    private String idempotencyStrategy;

    /**
     * 显式投递幂等键（可选）。
     *
     * <p>为空时默认使用 taskType:bizType:bizId 生成幂等键。
     * 该值会写入 bizUniqueKey，长度不能超过 schema 中的 256 字符。
     * 不建议放入手机号、身份证、Token 等敏感原文。
     * 幂等键是“同一业务意图”的稳定身份，不是任务执行锁；重复投递命中后会返回既有任务。
     */
    private String idempotencyKey;

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
