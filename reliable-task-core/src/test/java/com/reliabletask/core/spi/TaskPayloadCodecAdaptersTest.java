package com.reliabletask.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskPayloadCodecAdaptersTest {

    @Test
    @DisplayName("fromSerializer - 旧 serializer 可适配为 codec")
    void fromSerializer_delegatesToSerializer() {
        RecordingSerializer serializer = new RecordingSerializer();
        TaskPayloadCodec codec = TaskPayloadCodecAdapters.fromSerializer(serializer);
        TaskPayloadCodecContext context = TaskPayloadCodecContext.builder()
                .operation(TaskPayloadCodecContext.Operation.ENCODE)
                .taskType("TYPE_A")
                .build();

        String encoded = codec.encode("payload", context);
        String decoded = codec.decode("payload", String.class, TaskPayloadCodecContext.decode(String.class));

        assertEquals("encoded:payload", encoded);
        assertEquals("payload", decoded);
        assertSame(String.class, serializer.lastTargetType.get());
    }

    @Test
    @DisplayName("toSerializer - codec 可适配回旧 serializer")
    void toSerializer_delegatesToCodecWithFallbackContext() {
        RecordingCodec codec = new RecordingCodec();
        TaskPayloadSerializer serializer = TaskPayloadCodecAdapters.toSerializer(codec);

        String encoded = serializer.serialize("payload");
        String decoded = serializer.deserialize("decoded:payload", String.class);

        assertEquals("encoded:payload", encoded);
        assertEquals("payload", decoded);
        assertEquals(TaskPayloadCodecContext.Operation.ENCODE, codec.lastEncodeContext.get().getOperation());
        assertEquals(TaskPayloadCodecContext.Operation.DECODE, codec.lastDecodeContext.get().getOperation());
        assertSame(String.class, codec.lastDecodeContext.get().getTargetType());
    }

    private static class RecordingSerializer implements TaskPayloadSerializer {

        private final AtomicReference<Class<?>> lastTargetType = new AtomicReference<>();

        @Override
        public String serialize(Object payload) {
            return "encoded:" + payload;
        }

        @Override
        public <T> T deserialize(String payload, Class<T> targetType) {
            lastTargetType.set(targetType);
            return targetType.cast(payload);
        }
    }

    private static class RecordingCodec implements TaskPayloadCodec {

        private final AtomicReference<TaskPayloadCodecContext> lastEncodeContext = new AtomicReference<>();
        private final AtomicReference<TaskPayloadCodecContext> lastDecodeContext = new AtomicReference<>();

        @Override
        public String encode(Object payload, TaskPayloadCodecContext context) {
            lastEncodeContext.set(context);
            return "encoded:" + payload;
        }

        @Override
        public <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context) {
            lastDecodeContext.set(context);
            return targetType.cast(payload.replace("decoded:", ""));
        }
    }
}
