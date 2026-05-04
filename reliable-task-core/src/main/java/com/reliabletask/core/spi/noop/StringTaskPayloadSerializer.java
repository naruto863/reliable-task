package com.reliabletask.core.spi.noop;

import com.reliabletask.core.spi.TaskPayloadSerializer;

/**
 * V1.5 兼容 payload 序列化器，仅支持字符串透传。
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
