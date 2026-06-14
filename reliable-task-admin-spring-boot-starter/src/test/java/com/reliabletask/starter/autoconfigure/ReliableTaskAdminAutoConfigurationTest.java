package com.reliabletask.starter.autoconfigure;

import com.reliabletask.admin.controller.AdminQueryGuard;
import com.reliabletask.admin.controller.TaskAdminController;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import com.reliabletask.core.vo.ConsoleCapabilitiesVO;
import com.reliabletask.starter.config.ReliableTaskProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReliableTask Admin starter 自动配置测试")
class ReliableTaskAdminAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReliableTaskAutoConfiguration.class,
                    ReliableTaskAdminAutoConfiguration.class))
            .withUserConfiguration(TaskStoreTestConfiguration.class);

    @Test
    @DisplayName("admin 默认关闭且鉴权默认开启")
    void adminDisabledAndAuthEnabledByDefault_doesNotRegisterController() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TaskAdminController.class);
            assertThat(context).hasSingleBean(ReliableTaskProperties.class);
            ReliableTaskProperties properties = context.getBean(ReliableTaskProperties.class);
            assertThat(properties.getAdmin().isEnabled()).isFalse();
            assertThat(properties.getAdmin().isWriteEnabled()).isFalse();
            assertThat(properties.getAdmin().getAuth().isEnabled()).isTrue();
            assertThat(properties.getAdmin().getAudit().isEnabled()).isFalse();
            assertThat(properties.getAdmin().getBatch().isEnabled()).isFalse();
            assertThat(properties.getAdmin().getConsole().isPayloadPlaintextEnabled()).isFalse();
            assertThat(properties.getAdmin().getConsole().getPayloadPreviewLength()).isEqualTo(512);
            assertThat(properties.getAdmin().getConsole().isWriteConfirmationRequired()).isTrue();
            assertThat(properties.getAdmin().getPort()).isEqualTo(9090);
            assertThat(properties.getAdmin().getContextPath()).isEqualTo("/reliable-task");
        });
    }

    @Test
    @DisplayName("admin 显式启用且关闭鉴权时注册 TaskAdminController 和默认 provider")
    void adminExplicitlyEnabledAndAuthDisabled_registersController() {
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
                    assertThat(controller.cancel(1L, "admin", "trace-1", "true").getCode()).isEqualTo(404);
                    verify(taskStore, never()).cancelTask(anyLong());
                });
    }

    @Test
    @DisplayName("admin write-enabled=true 但 auth 关闭时拒绝写接口")
    void adminWriteEnabledButAuthDisabled_rejectsWriteOperations() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.write-enabled=true",
                        "reliable-task.admin.audit.enabled=true",
                        "reliable-task.admin.auth.enabled=false")
                .run(context -> {
                    TaskAdminController controller = context.getBean(TaskAdminController.class);
                    TaskStore taskStore = context.getBean(TaskStore.class);

                    assertThat(controller.cancel(1L, "admin", "trace-1", "true").getCode()).isEqualTo(403);
                    verify(taskStore, never()).cancelTask(1L);
                });
    }

    @Test
    @DisplayName("admin write-enabled=true 且 auth/audit/confirmation 满足时允许写接口进入 store")
    void adminWriteEnabledWithSafetyPreconditions_allowsWriteOperations() {
        TaskAuthorizationProvider provider = (operator, action, taskId) -> true;

        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.write-enabled=true",
                        "reliable-task.admin.audit.enabled=true",
                        "reliable-task.admin.auth.enabled=true")
                .withBean(TaskAuthorizationProvider.class, () -> provider)
                .run(context -> {
                    TaskAdminController controller = context.getBean(TaskAdminController.class);
                    TaskStore taskStore = context.getBean(TaskStore.class);
                    when(taskStore.cancelTask(1L)).thenReturn(true);

                    assertThat(controller.cancel(1L, "admin", "trace-1", "true").getCode()).isEqualTo(200);
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
    @DisplayName("admin query 配置会传递给 controller")
    void adminQueryProperties_arePassedToController() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.auth.enabled=false",
                        "reliable-task.admin.query.default-window-hours=12",
                        "reliable-task.admin.query.max-window-days=7",
                        "reliable-task.admin.query.default-limit=25",
                        "reliable-task.admin.query.max-limit=80",
                        "reliable-task.admin.query.slow-threshold-ms=45000")
                .run(context -> {
                    TaskAdminController controller = context.getBean(TaskAdminController.class);
                    AdminQueryGuard guard = (AdminQueryGuard) ReflectionTestUtils.getField(controller, "adminQueryGuard");

                    assertThat(guard).isNotNull();
                    assertThat(guard.getDefaultWindowHours()).isEqualTo(12);
                    assertThat(guard.getMaxWindowDays()).isEqualTo(7);
                    assertThat(guard.getDefaultLimit()).isEqualTo(25);
                    assertThat(guard.getMaxLimit()).isEqualTo(80);
                    assertThat(guard.getSlowThresholdMs()).isEqualTo(45_000L);
                });
    }

    @Test
    @DisplayName("admin console 配置会传递给 capabilities")
    void adminConsoleProperties_arePassedToCapabilities() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.auth.enabled=false",
                        "reliable-task.admin.write-enabled=true",
                        "reliable-task.admin.audit.enabled=true",
                        "reliable-task.admin.batch.enabled=true",
                        "reliable-task.admin.max-page-size=80",
                        "reliable-task.admin.max-batch-limit=25",
                        "reliable-task.admin.console.payload-plaintext-enabled=true",
                        "reliable-task.admin.console.payload-preview-length=64",
                        "reliable-task.admin.console.write-confirmation-required=false")
                .run(context -> {
                    TaskAdminController controller = context.getBean(TaskAdminController.class);

                    ConsoleCapabilitiesVO capabilities =
                            controller.getConsoleCapabilities("viewer").getData();

                    assertThat(capabilities.isAdminEnabled()).isTrue();
                    assertThat(capabilities.isWriteEnabled()).isTrue();
                    assertThat(capabilities.isAuthEnabled()).isFalse();
                    assertThat(capabilities.isAuditEnabled()).isTrue();
                    assertThat(capabilities.isBatchEnabled()).isTrue();
                    assertThat(capabilities.getMaxPageSize()).isEqualTo(80);
                    assertThat(capabilities.getMaxBatchLimit()).isEqualTo(25);
                    assertThat(capabilities.isPayloadPlaintextEnabled()).isTrue();
                    assertThat(capabilities.isPayloadRevealAllowed()).isTrue();
                    assertThat(capabilities.getPayloadPreviewLength()).isEqualTo(64);
                    assertThat(capabilities.isWriteConfirmationRequired()).isFalse();
                });
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
                .withPropertyValues("reliable-task.admin.enabled=true")
                .withBean(TaskAuthorizationProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskAdminController.class);
                    assertThat(context).getBean(TaskAuthorizationProvider.class).isSameAs(customProvider);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TaskStoreTestConfiguration {
        @Bean
        TaskStore taskStore() {
            return mock(TaskStore.class);
        }
    }
}
