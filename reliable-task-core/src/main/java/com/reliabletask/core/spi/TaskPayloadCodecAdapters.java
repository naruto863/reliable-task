package com.reliabletask.core.spi;

import java.util.Objects;

/**
 * {@link TaskPayloadSerializer} 与 {@link TaskPayloadCodec} 的兼容适配器。
 *
 * <p>{@link TaskPayloadSerializer} 是早期简单序列化 SPI，只知道 payload 和目标类型；
 * {@link TaskPayloadCodec} 额外携带上下文，便于后续按 taskType、版本、租户等信息扩展。
 * 适配器把两套 SPI 的兼容逻辑集中在这里，避免模板和执行器到处分支判断。
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
            // 老 SPI 没有显式上下文，这里至少把运行时类型透传给 Codec，保留多态序列化扩展空间。
            Class<?> payloadType = payload == null ? null : payload.getClass();
            return codec.encode(payload, TaskPayloadCodecContext.encode(payloadType));
        }

        @Override
        public <T> T deserialize(String payload, Class<T> targetType) {
            return codec.decode(payload, targetType, TaskPayloadCodecContext.decode(targetType));
        }
    }
}
