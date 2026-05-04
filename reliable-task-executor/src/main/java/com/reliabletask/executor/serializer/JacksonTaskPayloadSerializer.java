package com.reliabletask.executor.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliabletask.core.spi.TaskPayloadSerializer;

/**
 * 基于 Jackson 的任务 payload 序列化器。
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
            return targetType.cast(payload);
        }
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize task payload", e);
        }
    }
}
