package com.reliabletask.starter.autoconfigure;

import com.reliabletask.core.spi.AlarmNotifier;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.WorkerHeartbeatReporter;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.spi.noop.NoopWorkerHeartbeatReporter;
import com.reliabletask.core.spi.noop.NoopAlarmNotifier;
import com.reliabletask.executor.alert.AlertProperties;
import com.reliabletask.executor.alert.DefaultTaskAlertService;
import com.reliabletask.executor.alert.NoopTaskAlertService;
import com.reliabletask.executor.alert.TaskAlertScheduler;
import com.reliabletask.executor.alert.TaskAlertService;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import com.reliabletask.executor.handler.TaskExecutor;
import com.reliabletask.executor.handler.TaskHandlerRegistry;
import com.reliabletask.executor.template.TransactionAwareTaskTemplate;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;
import com.reliabletask.executor.worker.WorkerProperties;
import com.reliabletask.executor.worker.WorkerScheduler;
import com.reliabletask.starter.metrics.MicrometerTaskMetricsRecorder;
import com.reliabletask.starter.config.ReliableTaskProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ReliableTaskExecutorAutoConfiguration 测试")
class ReliableTaskExecutorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReliableTaskAutoConfiguration.class,
                    ReliableTaskExecutorAutoConfiguration.class))
            .withUserConfiguration(TaskStoreTestConfiguration.class);

    @Test
    @DisplayName("默认配置注册执行链路 Bean 并绑定默认值")
    void defaultConfiguration_registersExecutorBeansAndDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskExecutor.class);
            assertThat(context).hasSingleBean(TaskTemplate.class);
            assertThat(context).hasSingleBean(WorkerScheduler.class);
            assertThat(context).hasSingleBean(WorkerProperties.class);
            assertThat(context).hasSingleBean(TaskPayloadSerializer.class);
            assertThat(context).hasSingleBean(TaskMetricsRecorder.class);
            assertThat(context).hasSingleBean(TaskAuditRecorder.class);
            assertThat(context).hasSingleBean(WorkerHeartbeatReporter.class);
            assertThat(context).hasSingleBean(AlarmNotifier.class);
            assertThat(context).hasSingleBean(TaskAlertService.class);
            assertThat(context).doesNotHaveBean(TaskAlertScheduler.class);
            assertThat(context).getBean(TaskPayloadSerializer.class)
                    .isInstanceOf(JacksonTaskPayloadSerializer.class);
            assertThat(context).getBean(TaskMetricsRecorder.class)
                    .isInstanceOf(NoopTaskMetricsRecorder.class);
            assertThat(context).getBean(TaskAuditRecorder.class)
                    .isInstanceOf(NoopTaskAuditRecorder.class);
            assertThat(context).getBean(WorkerHeartbeatReporter.class)
                    .isInstanceOf(NoopWorkerHeartbeatReporter.class);
            assertThat(context).getBean(AlarmNotifier.class)
                    .isInstanceOf(NoopAlarmNotifier.class);
            assertThat(context).getBean(TaskAlertService.class)
                    .isInstanceOf(NoopTaskAlertService.class);
            assertThat(context).hasBean("strictUniqueIdempotencyStrategy");
            assertThat(context).hasBean("allowAfterTerminalIdempotencyStrategy");
            assertThat(context).getBeans(IdempotencyStrategy.class)
                    .hasSize(2);
            assertThat(context).getBean("strictUniqueIdempotencyStrategy")
                    .isInstanceOf(StrictUniqueIdempotencyStrategy.class);
            assertThat(context).getBean("allowAfterTerminalIdempotencyStrategy")
                    .isInstanceOf(AllowAfterTerminalIdempotencyStrategy.class);
            assertThat(context).getBean(TaskTemplate.class)
                    .isInstanceOf(TransactionAwareTaskTemplate.class);
            assertThat(context).getBean(WorkerProperties.class).satisfies(worker -> {
                assertThat(worker.isEnabled()).isTrue();
                assertThat(worker.getPollIntervalMs()).isEqualTo(5000L);
                assertThat(worker.getBatchSize()).isEqualTo(10);
                assertThat(worker.isBackpressureEnabled()).isFalse();
                assertThat(worker.getBackpressureMinFetchSize()).isEqualTo(1);
                assertThat(worker.getBackpressureMaxFetchSize()).isEqualTo(10);
                assertThat(worker.isHeartbeatEnabled()).isFalse();
                assertThat(worker.getHeartbeatIntervalMs()).isEqualTo(10000L);
                assertThat(worker.getLockRenewalTtlSeconds()).isEqualTo(300L);
                assertThat(worker.getStaleWorkerThresholdSeconds()).isEqualTo(60L);
            });
        });
    }

    @Test
    @DisplayName("自定义 TaskTemplate 时自动配置不覆盖")
    void customTaskTemplate_isNotOverridden() {
        TaskTemplate customTaskTemplate = mock(TaskTemplate.class);

        contextRunner
                .withBean(TaskTemplate.class, () -> customTaskTemplate)
                .run(context ->
                        assertThat(context).getBean(TaskTemplate.class).isSameAs(customTaskTemplate));
    }

    @Test
    @DisplayName("自定义 V2 SPI Bean 时自动配置不覆盖")
    void customV2SpiBeans_areNotOverridden() {
        TaskPayloadSerializer customSerializer = mock(TaskPayloadSerializer.class);
        TaskMetricsRecorder customMetricsRecorder = mock(TaskMetricsRecorder.class);
        TaskAuditRecorder customAuditRecorder = mock(TaskAuditRecorder.class);
        WorkerHeartbeatReporter customHeartbeatReporter = mock(WorkerHeartbeatReporter.class);

        contextRunner
                .withBean(TaskPayloadSerializer.class, () -> customSerializer)
                .withBean(TaskMetricsRecorder.class, () -> customMetricsRecorder)
                .withBean(TaskAuditRecorder.class, () -> customAuditRecorder)
                .withBean(WorkerHeartbeatReporter.class, () -> customHeartbeatReporter)
                .run(context -> {
                    assertThat(context).getBean(TaskPayloadSerializer.class).isSameAs(customSerializer);
                    assertThat(context).getBean(TaskMetricsRecorder.class).isSameAs(customMetricsRecorder);
                    assertThat(context).getBean(TaskAuditRecorder.class).isSameAs(customAuditRecorder);
                    assertThat(context).getBean(WorkerHeartbeatReporter.class).isSameAs(customHeartbeatReporter);
                });
    }

    @Test
    @DisplayName("metrics 启用且存在 MeterRegistry 时装配 Micrometer recorder")
    void metricsEnabledWithMeterRegistry_registersMicrometerRecorder() {
        contextRunner
                .withPropertyValues("reliable-task.metrics.enabled=true")
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context ->
                        assertThat(context).getBean(TaskMetricsRecorder.class)
                                .isInstanceOf(MicrometerTaskMetricsRecorder.class));
    }

    @Test
    @DisplayName("alert 启用时装配告警服务和调度器")
    void alertEnabled_registersAlertServiceAndScheduler() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.alert.enabled=true",
                        "reliable-task.alert.pending-threshold=100",
                        "reliable-task.alert.failure-rate-threshold=0.5",
                        "reliable-task.alert.window-seconds=60"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskAlertScheduler.class);
                    assertThat(context).getBean(TaskAlertService.class)
                            .isInstanceOf(DefaultTaskAlertService.class);
                    assertThat(context).getBean(AlertProperties.class).satisfies(alert -> {
                        assertThat(alert.isEnabled()).isTrue();
                        assertThat(alert.getPendingThreshold()).isEqualTo(100L);
                        assertThat(alert.getFailureRateThreshold()).isEqualTo(0.5D);
                        assertThat(alert.getWindowSeconds()).isEqualTo(60L);
                    });
                });
    }

    @Test
    @DisplayName("worker 关闭时不注册 WorkerScheduler")
    void workerDisabled_doesNotRegisterWorkerScheduler() {
        contextRunner
                .withPropertyValues("reliable-task.worker.enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(WorkerScheduler.class));
    }

    @Test
    @DisplayName("@TaskHandler Bean 只注册一次")
    void annotatedTaskHandlerBean_isRegisteredOnce() {
        contextRunner
                .withUserConfiguration(AnnotatedTaskHandlerTestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(TaskHandlerRegistry.class)
                            .satisfies(registry -> assertThat(registry.hasHandler("CREATE_SHIPMENT")).isTrue());
                });
    }

    @Test
    @DisplayName("V2 配置默认值保持兼容")
    void v2ConfigurationDefaults_preserveV15Behavior() {
        contextRunner.run(context -> {
            ReliableTaskProperties properties = context.getBean(ReliableTaskProperties.class);

            assertThat(properties.getMetrics().isEnabled()).isFalse();
            assertThat(properties.getAlert().isEnabled()).isFalse();
            assertThat(properties.getAlert().getPendingThreshold()).isEqualTo(0L);
            assertThat(properties.getAlert().getFailureRateThreshold()).isEqualTo(1.0D);
            assertThat(properties.getAlert().getWindowSeconds()).isEqualTo(300L);
            assertThat(properties.getIdempotency().getStrategy()).isEqualTo("STRICT_UNIQUE");
            assertThat(properties.getSerializer().getType()).isEqualTo("JACKSON");
            assertThat(properties.getWorker().getBackpressure().isEnabled()).isFalse();
            assertThat(properties.getWorker().getBackpressure().getMinFetchSize()).isEqualTo(1);
            assertThat(properties.getWorker().getBackpressure().getMaxFetchSize()).isEqualTo(10);
            assertThat(properties.getWorker().getHeartbeat().isEnabled()).isFalse();
            assertThat(properties.getWorker().getHeartbeat().getIntervalMs()).isEqualTo(10000L);
            assertThat(properties.getWorker().getHeartbeat().getLockRenewalTtlSeconds()).isEqualTo(300L);
            assertThat(properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds()).isEqualTo(60L);
            assertThat(properties.getAdmin().getAudit().isEnabled()).isFalse();
            assertThat(properties.getAdmin().getAuth().isEnabled()).isFalse();
            assertThat(properties.getAdmin().getBatch().isEnabled()).isFalse();
        });
    }

    @Test
    @DisplayName("V2 配置可通过 reliable-task 前缀绑定")
    void v2Configuration_canBindFromProperties() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.metrics.enabled=true",
                        "reliable-task.alert.enabled=true",
                        "reliable-task.alert.pending-threshold=10",
                        "reliable-task.alert.failure-rate-threshold=0.7",
                        "reliable-task.alert.window-seconds=120",
                        "reliable-task.idempotency.strategy=ALLOW_AFTER_TERMINAL",
                        "reliable-task.serializer.type=CUSTOM",
                        "reliable-task.worker.backpressure.enabled=true",
                        "reliable-task.worker.backpressure.min-fetch-size=2",
                        "reliable-task.worker.backpressure.max-fetch-size=6",
                        "reliable-task.worker.heartbeat.enabled=true",
                        "reliable-task.worker.heartbeat.interval-ms=2000",
                        "reliable-task.worker.heartbeat.lock-renewal-ttl-seconds=120",
                        "reliable-task.worker.heartbeat.stale-worker-threshold-seconds=30",
                        "reliable-task.admin.audit.enabled=true",
                        "reliable-task.admin.auth.enabled=true",
                        "reliable-task.admin.batch.enabled=true"
                )
                .run(context -> {
                    ReliableTaskProperties properties = context.getBean(ReliableTaskProperties.class);

                    assertThat(properties.getMetrics().isEnabled()).isTrue();
                    assertThat(properties.getAlert().isEnabled()).isTrue();
                    assertThat(properties.getAlert().getPendingThreshold()).isEqualTo(10L);
                    assertThat(properties.getAlert().getFailureRateThreshold()).isEqualTo(0.7D);
                    assertThat(properties.getAlert().getWindowSeconds()).isEqualTo(120L);
                    assertThat(properties.getIdempotency().getStrategy()).isEqualTo("ALLOW_AFTER_TERMINAL");
                    assertThat(properties.getSerializer().getType()).isEqualTo("CUSTOM");
                    assertThat(properties.getWorker().getBackpressure().isEnabled()).isTrue();
                    assertThat(properties.getWorker().getBackpressure().getMinFetchSize()).isEqualTo(2);
                    assertThat(properties.getWorker().getBackpressure().getMaxFetchSize()).isEqualTo(6);
                    assertThat(properties.getWorker().getHeartbeat().isEnabled()).isTrue();
                    assertThat(properties.getWorker().getHeartbeat().getIntervalMs()).isEqualTo(2000L);
                    assertThat(properties.getWorker().getHeartbeat().getLockRenewalTtlSeconds()).isEqualTo(120L);
                    assertThat(properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds()).isEqualTo(30L);
                    assertThat(properties.getAdmin().getAudit().isEnabled()).isTrue();
                    assertThat(properties.getAdmin().getAuth().isEnabled()).isTrue();
                    assertThat(properties.getAdmin().getBatch().isEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TaskStoreTestConfiguration {
        @Bean
        TaskStore taskStore() {
            return mock(TaskStore.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedTaskHandlerTestConfiguration {
        @Bean
        TestCreateShipmentHandler testCreateShipmentHandler() {
            return new TestCreateShipmentHandler();
        }
    }

    @com.reliabletask.core.annotation.TaskHandler("CREATE_SHIPMENT")
    static class TestCreateShipmentHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "CREATE_SHIPMENT";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证注册行为，不执行任务。
        }
    }
}
