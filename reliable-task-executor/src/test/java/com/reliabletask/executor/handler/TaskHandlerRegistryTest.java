package com.reliabletask.executor.handler;

import com.reliabletask.core.model.TaskHandlerMetadata;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TaskHandlerRegistry 测试")
class TaskHandlerRegistryTest {

    @Test
    @DisplayName("注册 Handler 时保存 metadata")
    void registerHandler_storesMetadata() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        MetadataHandler handler = new MetadataHandler();

        registry.registerHandler(handler);

        TaskHandlerMetadata metadata = registry.getMetadata("METADATA_TASK");
        assertThat(metadata.getTaskType()).isEqualTo("METADATA_TASK");
        assertThat(metadata.getHandlerClassName()).isEqualTo(MetadataHandler.class.getName());
        assertThat(metadata.getPayloadType()).isEqualTo(Integer.class);
        assertThat(metadata.getMaxConcurrency()).isEqualTo(3);
        assertThat(metadata.getTimeoutMs()).isEqualTo(1500L);
        assertThat(registry.getAllMetadata()).containsKey("METADATA_TASK");
    }

    @Test
    @DisplayName("使用解析后的 taskType 注册时仍检测重复")
    void registerHandler_withResolvedTaskTypeRejectsDuplicate() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        TaskHandler first = new FirstCustomNamedHandler();
        TaskHandler second = new SecondCustomNamedHandler();

        registry.registerHandler("CUSTOM_NAME", first, FirstCustomNamedHandler.class, null);

        assertThatThrownBy(() -> registry.registerHandler("CUSTOM_NAME", second,
                SecondCustomNamedHandler.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate TaskHandler for taskType: CUSTOM_NAME");
    }

    @Test
    @DisplayName("空白 taskType 注册失败")
    void registerHandler_withBlankTaskTypeRejects() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();

        assertThatThrownBy(() -> registry.registerHandler(" ", new MetadataHandler(),
                MetadataHandler.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TaskHandler taskType must not be blank");
    }

    static class MetadataHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "METADATA_TASK";
        }

        @Override
        public Class<?> payloadType() {
            return Integer.class;
        }

        @Override
        public int maxConcurrency() {
            return 3;
        }

        @Override
        public long timeoutMs() {
            return 1500L;
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }

    static class FirstCustomNamedHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "FIRST";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }

    static class SecondCustomNamedHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "SECOND";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }
}
