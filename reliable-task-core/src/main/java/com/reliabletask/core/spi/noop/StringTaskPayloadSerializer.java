package com.reliabletask.core.spi.noop;

import com.reliabletask.core.spi.TaskPayloadSerializer;

/**
 * V1.5 兼容 payload 序列化器，仅支持字符串透传。
 *
 * <p>该实现保留给早期只提交 JSON 字符串的调用方。对象 payload 应使用
 * TaskPayloadCodec 或 JacksonTaskPayloadSerializer，否则反序列化到非 String 类型会失败。
 */
public class StringTaskPayloadSerializer implements TaskPayloadSerializer {

    @Override
    public String serialize(Object payload) {
        return payload == null ? null : payload.toString();
    }

    @Override
    public <T> T deserialize(String payload, Class<T> targetType) {
        if (targetType == String.class) {
            return targetType.cast(payload);
        }
        throw new IllegalArgumentException("StringTaskPayloadSerializer only supports String target type");
    }
}
