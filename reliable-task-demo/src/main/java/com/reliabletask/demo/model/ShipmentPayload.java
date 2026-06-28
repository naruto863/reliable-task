package com.reliabletask.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demo 发货任务对象 payload。
 *
 * <p>用于演示对象 payload 的序列化/反序列化路径。字段保持普通 Java Bean 形态，
 * 方便 JacksonTaskPayloadSerializer 根据 Handler 声明的 payloadType 还原对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentPayload {

    private String orderNo;

    private String buyerId;

    private String address;

    /**
     * 演示不可重试失败分类。
     *
     * <p>设置为 true 时 Handler 会抛出 NonRetryableException，任务应直接进入 DEAD，
     * 不再消耗剩余重试次数。
     */
    private boolean forceNonRetryable;
}
