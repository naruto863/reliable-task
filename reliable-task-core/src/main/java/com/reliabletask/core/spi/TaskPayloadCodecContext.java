package com.reliabletask.core.spi;

import lombok.Builder;
import lombok.Value;

/**
 * payload codec 执行上下文。
 *
 * <p>该对象只携带当前任务已存在的元数据，避免改变 payload 入库格式或新增 schema 字段。
 */
@Value
@Builder
public class TaskPayloadCodecContext {

    Operation operation;
    Long taskId;
    String taskType;
    String bizType;
    String bizId;
    String tenantId;
    String shardKey;
    String traceId;
    Class<?> targetType;

    public static TaskPayloadCodecContext encode(Class<?> payloadType) {
        return TaskPayloadCodecContext.builder()
                .operation(Operation.ENCODE)
                .targetType(payloadType)
                .build();
    }

    public static TaskPayloadCodecContext decode(Class<?> targetType) {
        return TaskPayloadCodecContext.builder()
                .operation(Operation.DECODE)
                .targetType(targetType)
                .build();
    }

    public enum Operation {
        ENCODE,
        DECODE
    }
}
