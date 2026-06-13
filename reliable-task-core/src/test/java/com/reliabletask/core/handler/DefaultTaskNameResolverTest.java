package com.reliabletask.core.handler;

import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.TaskHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultTaskNameResolver 测试")
class DefaultTaskNameResolverTest {

    private final DefaultTaskNameResolver resolver = new DefaultTaskNameResolver();

    @Test
    @DisplayName("注解与 getTaskType 一致时返回 taskType")
    void resolve_withMatchingAnnotation_returnsTaskType() {
        MatchingHandler handler = new MatchingHandler();

        String taskType = resolver.resolve(handler, MatchingHandler.class,
                MatchingHandler.class.getAnnotation(com.reliabletask.core.annotation.TaskHandler.class));

        assertThat(taskType).isEqualTo("CREATE_SHIPMENT");
    }

    @Test
    @DisplayName("无注解时使用 getTaskType")
    void resolve_withoutAnnotation_returnsHandlerTaskType() {
        NoAnnotationHandler handler = new NoAnnotationHandler();

        String taskType = resolver.resolve(handler, NoAnnotationHandler.class, null);

        assertThat(taskType).isEqualTo("NO_ANNOTATION");
    }

    @Test
    @DisplayName("注解与 getTaskType 不一致时失败")
    void resolve_withMismatchedAnnotation_throwsException() {
        MismatchedHandler handler = new MismatchedHandler();

        assertThatThrownBy(() -> resolver.resolve(handler, MismatchedHandler.class,
                MismatchedHandler.class.getAnnotation(com.reliabletask.core.annotation.TaskHandler.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@TaskHandler value must match getTaskType()");
    }

    @Test
    @DisplayName("空白 taskType 失败")
    void resolve_withBlankTaskType_throwsException() {
        BlankHandler handler = new BlankHandler();

        assertThatThrownBy(() -> resolver.resolve(handler, BlankHandler.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TaskHandler taskType must not be blank");
    }

    @com.reliabletask.core.annotation.TaskHandler("CREATE_SHIPMENT")
    static class MatchingHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "CREATE_SHIPMENT";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }

    static class NoAnnotationHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "NO_ANNOTATION";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }

    @com.reliabletask.core.annotation.TaskHandler("ANNOTATED")
    static class MismatchedHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return "METHOD";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }

    static class BlankHandler implements TaskHandler {
        @Override
        public String getTaskType() {
            return " ";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试不执行任务。
        }
    }
}
