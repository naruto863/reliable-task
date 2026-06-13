package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TaskHandler 注册元数据。
 *
 * <p>该模型只描述内存注册中心中的 Handler 能力，用于文档、Admin 展示和后续控制台扩展；
 * v0.6 不新增数据库表持久化这些信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHandlerMetadata {

    /**
     * Handler 最终注册的 taskType。
     */
    private String taskType;

    /**
     * Handler 实现类全限定名。
     */
    private String handlerClassName;

    /**
     * Handler 声明的 payload 目标类型。
     */
    private Class<?> payloadType;

    /**
     * Handler 最大并发数，0 表示不限制。
     */
    private int maxConcurrency;

    /**
     * Handler 单次执行超时时间，单位毫秒。
     */
    private long timeoutMs;

    /**
     * Handler 描述信息，可为空。
     */
    private String description;
}
