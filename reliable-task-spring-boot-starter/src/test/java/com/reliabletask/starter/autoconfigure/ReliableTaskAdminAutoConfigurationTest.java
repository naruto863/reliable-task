package com.reliabletask.starter.autoconfigure;

import com.reliabletask.admin.controller.TaskAdminController;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReliableTaskAdminAutoConfiguration 测试")
class ReliableTaskAdminAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReliableTaskAutoConfiguration.class,
                    ReliableTaskAdminAutoConfiguration.class))
            .withUserConfiguration(TaskStoreTestConfiguration.class);

    @Test
    @DisplayName("admin 启用且关闭鉴权时注册 TaskAdminController 和默认 provider")
    void adminEnabledByDefault_registersController() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.auth.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskAdminController.class);
                    assertThat(context).hasSingleBean(TaskAuthorizationProvider.class);
                    assertThat(context).getBean(TaskAuthorizationProvider.class)
                            .isInstanceOf(NoopTaskAuthorizationProvider.class);
                    TaskAdminController controller = context.getBean(TaskAdminController.class);
                    TaskStore taskStore = context.getBean(TaskStore.class);
                    assertThat(controller.cancel(1L, "admin", "trace-1").getCode()).isEqualTo(404);
                    verify(taskStore, never()).cancelTask(anyLong());
                });
    }

    @Test
    @DisplayName("admin write-enabled=true 时允许写接口进入 store")
    void adminWriteEnabled_allowsWriteOperations() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.write-enabled=true",
                        "reliable-task.admin.auth.enabled=false")
                .run(context -> {
                    TaskAdminController controller = context.getBean(TaskAdminController.class);
                    TaskStore taskStore = context.getBean(TaskStore.class);
                    when(taskStore.cancelTask(1L)).thenReturn(true);

                    assertThat(controller.cancel(1L, "admin", "trace-1").getCode()).isEqualTo(200);
                    verify(taskStore).cancelTask(1L);
                });
    }

    @Test
    @DisplayName("admin 显式关闭时不注册 TaskAdminController")
    void adminDisabled_doesNotRegisterController() {
        contextRunner
                .withPropertyValues("reliable-task.admin.enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(TaskAdminController.class));
    }

    @Test
    @DisplayName("auth 开启且没有自定义 provider 时不注册默认 provider")
    void authEnabledWithoutProvider_doesNotRegisterNoopProvider() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskAdminController.class);
                    assertThat(context).doesNotHaveBean(TaskAuthorizationProvider.class);
                });
    }

    @Test
    @DisplayName("自定义权限 provider 时自动配置不覆盖")
    void customAuthorizationProvider_isNotOverridden() {
        TaskAuthorizationProvider customProvider = mock(TaskAuthorizationProvider.class);

        contextRunner
                .withBean(TaskAuthorizationProvider.class, () -> customProvider)
                .run(context ->
                        assertThat(context).getBean(TaskAuthorizationProvider.class).isSameAs(customProvider));
    }

    @Configuration(proxyBeanMethods = false)
    static class TaskStoreTestConfiguration {
        @Bean
        TaskStore taskStore() {
            return mock(TaskStore.class);
        }
    }
}
