package com.reliabletask.executor.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliabletask.core.spi.TaskPayloadSerializer;

/**
 * 基于 Jackson 的任务 payload 序列化器。
 *
 * <p>字符串 payload 会原样透传，用于兼容早期调用方已经自行生成 JSON 字符串的场景；
 * 对象 payload 才交给 Jackson 序列化。反序列化时目标类型为 String 也保持原样返回。
 */
public class JacksonTaskPayloadSerializer implements TaskPayloadSerializer {

    private final ObjectMapper objectMapper;

    public JacksonTaskPayloadSerializer() {
        this(new ObjectMapper());
    }

    public JacksonTaskPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String stringPayload) {
            // 兼容字符串入口，避免把已经是 JSON 的字符串再次编码成带引号的 JSON 字符串。
            return stringPayload;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize task payload", e);
        }
    }

    @Override
    public <T> T deserialize(String payload, Class<T> targetType) {
        if (payload == null) {
            return null;
        }
        if (targetType == String.class) {
            // Handler 显式声明 String 时，说明业务方希望拿到入库原文。
            return targetType.cast(payload);
        }
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize task payload", e);
        }
    }
}
