package com.reliabletask.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
