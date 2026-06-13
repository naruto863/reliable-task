package com.reliabletask.core.spi;

import java.util.Objects;

/**
 * {@link TaskPayloadSerializer} 与 {@link TaskPayloadCodec} 的兼容适配器。
 */
public final class TaskPayloadCodecAdapters {

    private TaskPayloadCodecAdapters() {
    }

    public static TaskPayloadCodec fromSerializer(TaskPayloadSerializer serializer) {
        return new SerializerBackedTaskPayloadCodec(Objects.requireNonNull(serializer, "serializer must not be null"));
    }

    public static TaskPayloadSerializer toSerializer(TaskPayloadCodec codec) {
        return new CodecBackedTaskPayloadSerializer(Objects.requireNonNull(codec, "codec must not be null"));
    }

    private record SerializerBackedTaskPayloadCodec(TaskPayloadSerializer serializer) implements TaskPayloadCodec {

        @Override
        public String encode(Object payload, TaskPayloadCodecContext context) {
            return serializer.serialize(payload);
        }

        @Override
        public <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context) {
            return serializer.deserialize(payload, targetType);
        }
    }

    private record CodecBackedTaskPayloadSerializer(TaskPayloadCodec codec) implements TaskPayloadSerializer {

        @Override
        public String serialize(Object payload) {
            Class<?> payloadType = payload == null ? null : payload.getClass();
            return codec.encode(payload, TaskPayloadCodecContext.encode(payloadType));
        }

        @Override
        public <T> T deserialize(String payload, Class<T> targetType) {
            return codec.decode(payload, targetType, TaskPayloadCodecContext.decode(targetType));
        }
    }
}
