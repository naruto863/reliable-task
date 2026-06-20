package com.reliabletask.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskSerializerCompatibilityTest {

    @Test
    @DisplayName("TaskSerializer - deprecated 旧接口仍保持源码兼容")
    @SuppressWarnings("deprecation")
    void deprecatedTaskSerializer_keepsSourceCompatibility() {
        TaskSerializer serializer = new LegacyTaskSerializer();

        String payload = serializer.serialize("payload");

        assertEquals("payload", payload);
        assertEquals("payload", serializer.deserialize(payload, String.class));
    }

    @Test
    @DisplayName("TaskSerializer - v1.0 保留 deprecated 兼容且不标记删除")
    void deprecatedTaskSerializer_isRetainedForV10Compatibility() {
        Deprecated deprecated = TaskSerializer.class.getAnnotation(Deprecated.class);

        assertNotNull(deprecated);
        assertEquals("0.6.0", deprecated.since());
        assertFalse(deprecated.forRemoval());
    }

    @SuppressWarnings("deprecation")
    private static class LegacyTaskSerializer implements TaskSerializer {

        @Override
        public String serialize(Object obj) {
            return obj == null ? null : obj.toString();
        }

        @Override
        public <T> T deserialize(String json, Class<T> clazz) {
            return clazz.cast(json);
        }
    }
}
