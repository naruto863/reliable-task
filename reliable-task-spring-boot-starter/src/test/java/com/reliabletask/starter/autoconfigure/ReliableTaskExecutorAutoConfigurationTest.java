package com.reliabletask.starter.autoconfigure;

import com.reliabletask.core.spi.AlarmNotifier;
import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.classifier.DefaultFailureClassifier;
import com.reliabletask.core.deadletter.TaskDeadLetterDispatcher;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.model.DeadLetterContext;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskHandlerMetadata;
import com.reliabletask.core.spi.FailureClassifier;
import com.reliabletask.core.spi.RetryStrategy;
import com.reliabletask.core.spi.TaskDeadLetterHandler;
import com.reliabletask.core.spi.TaskEventListener;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadCodec;
import com.reliabletask.core.spi.TaskPayloadCodecContext;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskNameResolver;
import com.reliabletask.core.spi.TaskTraceIdGenerator;
import com.reliabletask.core.spi.WorkerHeartbeatReporter;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskDeadLetterHandler;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.spi.noop.NoopWorkerHeartbeatReporter;
import com.reliabletask.core.spi.noop.NoopAlarmNotifier;
import com.reliabletask.core.trace.DefaultTaskTraceIdGenerator;
import com.reliabletask.executor.alert.AlertProperties;
import com.reliabletask.executor.alert.DefaultTaskAlertService;
import com.reliabletask.executor.alert.NoopTaskAlertService;
import com.reliabletask.executor.alert.TaskAlertScheduler;
import com.reliabletask.executor.alert.TaskAlertService;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import com.reliabletask.core.handler.DefaultTaskNameResolver;
import com.reliabletask.executor.handler.TaskExecutor;
import com.reliabletask.executor.handler.TaskHandlerRegistry;
import com.reliabletask.executor.interceptor.TaskExecutionContext;
import com.reliabletask.executor.interceptor.TaskInterceptor;
import com.reliabletask.executor.interceptor.TaskInterceptorChain;
import com.reliabletask.executor.interceptor.TraceTaskInterceptor;
import com.reliabletask.executor.recovery.RecoveryProperties;
import com.reliabletask.executor.retry.RetryProperties;
import com.reliabletask.core.strategy.RetryStrategyRegistry;
import com.reliabletask.executor.template.TransactionAwareTaskTemplate;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;
import com.reliabletask.executor.worker.WorkerProperties;
import com.reliabletask.executor.worker.WorkerScheduler;
import com.reliabletask.executor.threadpool.ThreadPoolProperties;
import com.reliabletask.starter.metrics.MicrometerTaskMetricsRecorder;
import com.reliabletask.starter.metrics.MicrometerTaskEventListener;
import com.reliabletask.starter.config.ReliableTaskProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(context).hasSingleBean(RecoveryProperties.class);
            assertThat(context).hasSingleBean(TaskPayloadSerializer.class);
            assertThat(context).hasSingleBean(TaskPayloadCodec.class);
            assertThat(context).hasSingleBean(TaskNameResolver.class);
            assertThat(context).hasSingleBean(TaskTraceIdGenerator.class);
            assertThat(context).hasSingleBean(TraceTaskInterceptor.class);
            assertThat(context).hasSingleBean(TaskInterceptorChain.class);
            assertThat(context).hasSingleBean(TaskMetricsRecorder.class);
            assertThat(context).hasSingleBean(TaskAuditRecorder.class);
            assertThat(context).hasSingleBean(WorkerHeartbeatReporter.class);
            assertThat(context).hasSingleBean(AlarmNotifier.class);
            assertThat(context).hasSingleBean(TaskAlertService.class);
            assertThat(context).hasSingleBean(FailureClassifier.class);
            assertThat(context).hasSingleBean(TaskDeadLetterHandler.class);
            assertThat(context).hasSingleBean(TaskDeadLetterDispatcher.class);
            assertThat(context).hasSingleBean(RetryProperties.class);
            assertThat(context).hasSingleBean(RetryStrategyRegistry.class);
            assertThat(context).hasSingleBean(TaskEventPublisher.class);
            assertThat(context).doesNotHaveBean(TaskAlertScheduler.class);
            assertThat(context).getBean(TaskPayloadSerializer.class)
                    .isInstanceOf(JacksonTaskPayloadSerializer.class);
            assertThat(context).getBean(TaskNameResolver.class)
                    .isInstanceOf(DefaultTaskNameResolver.class);
            assertThat(context).getBean(TaskTraceIdGenerator.class)
                    .isInstanceOf(DefaultTaskTraceIdGenerator.class);
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
            assertThat(context).getBean(FailureClassifier.class)
                    .isInstanceOf(DefaultFailureClassifier.class);
            assertThat(context).getBean(TaskDeadLetterHandler.class)
                    .isInstanceOf(NoopTaskDeadLetterHandler.class);
            assertThat(context).getBean(RetryStrategyRegistry.class)
                    .satisfies(registry -> assertThat(registry.getStrategy(RetryStrategyType.EXPONENTIAL))
                            .isNotNull());
            assertThat(context).getBean(RetryProperties.class).satisfies(retry -> {
                assertThat(retry.getExponentialMultiplier()).isEqualTo(2.0D);
                assertThat(retry.getJitterRatio()).isEqualTo(0.0D);
                assertThat(retry.getMinDelayMs()).isEqualTo(0L);
                assertThat(retry.getMaxDelayMs()).isEqualTo(300000L);
            });
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
                assertThat(worker.getMaxBatchSize()).isEqualTo(1000);
                assertThat(worker.isBackpressureEnabled()).isFalse();
                assertThat(worker.getBackpressureMinFetchSize()).isEqualTo(1);
                assertThat(worker.getBackpressureMaxFetchSize()).isEqualTo(10);
                assertThat(worker.isHeartbeatEnabled()).isFalse();
                assertThat(worker.getHeartbeatIntervalMs()).isEqualTo(10000L);
                assertThat(worker.getLockRenewalTtlSeconds()).isEqualTo(300L);
                assertThat(worker.getStaleWorkerThresholdSeconds()).isEqualTo(60L);
            });
            assertThat(context).getBean(RecoveryProperties.class).satisfies(recovery -> {
                assertThat(recovery.isEnabled()).isTrue();
                assertThat(recovery.getIntervalMs()).isEqualTo(30000L);
                assertThat(recovery.getTimeoutSeconds()).isEqualTo(300L);
                assertThat(recovery.getMaxResetPerScan()).isEqualTo(100);
            });
        });
    }

    @Test
    @DisplayName("自定义 TaskTemplate 时自动配置不覆盖")
    void customTaskTemplate_isNotOverridden() {
        TaskTemplate customTaskTemplate = stub(TaskTemplate.class);

        contextRunner
                .withBean(TaskTemplate.class, () -> customTaskTemplate)
                .run(context ->
                        assertThat(context).getBean(TaskTemplate.class).isSameAs(customTaskTemplate));
    }

    @Test
    @DisplayName("自定义 V2 SPI Bean 时自动配置不覆盖")
    void customV2SpiBeans_areNotOverridden() {
        TaskPayloadSerializer customSerializer = stub(TaskPayloadSerializer.class);
        TaskPayloadCodec customCodec = stub(TaskPayloadCodec.class);
        TaskMetricsRecorder customMetricsRecorder = stub(TaskMetricsRecorder.class);
        TaskAuditRecorder customAuditRecorder = stub(TaskAuditRecorder.class);
        WorkerHeartbeatReporter customHeartbeatReporter = stub(WorkerHeartbeatReporter.class);

        contextRunner
                .withBean(TaskPayloadSerializer.class, () -> customSerializer)
                .withBean(TaskPayloadCodec.class, () -> customCodec)
                .withBean(TaskMetricsRecorder.class, () -> customMetricsRecorder)
                .withBean(TaskAuditRecorder.class, () -> customAuditRecorder)
                .withBean(WorkerHeartbeatReporter.class, () -> customHeartbeatReporter)
                .run(context -> {
                    assertThat(context).getBean(TaskPayloadSerializer.class).isSameAs(customSerializer);
                    assertThat(context).getBean(TaskPayloadCodec.class).isSameAs(customCodec);
                    assertThat(context).getBean(TaskMetricsRecorder.class).isSameAs(customMetricsRecorder);
                    assertThat(context).getBean(TaskAuditRecorder.class).isSameAs(customAuditRecorder);
                    assertThat(context).getBean(WorkerHeartbeatReporter.class).isSameAs(customHeartbeatReporter);
                });
    }

    @Test
    @DisplayName("自定义 TaskPayloadSerializer Bean 会适配为默认 codec")
    void customTaskPayloadSerializer_isAdaptedToCodec() {
        TaskPayloadSerializer customSerializer = new TaskPayloadSerializer() {
            @Override
            public String serialize(Object payload) {
                return "serializer:" + payload;
            }

            @Override
            public <T> T deserialize(String payload, Class<T> targetType) {
                return targetType.cast(payload.replace("serializer:", ""));
            }
        };

        contextRunner
                .withBean(TaskPayloadSerializer.class, () -> customSerializer)
                .run(context -> {
                    assertThat(context).getBean(TaskPayloadSerializer.class).isSameAs(customSerializer);
                    TaskPayloadCodec codec = context.getBean(TaskPayloadCodec.class);

                    assertThat(codec.encode("payload", TaskPayloadCodecContext.encode(String.class)))
                            .isEqualTo("serializer:payload");
                    assertThat(codec.decode("serializer:payload", String.class,
                            TaskPayloadCodecContext.decode(String.class))).isEqualTo("payload");
                });
    }

    @Test
    @DisplayName("自定义 TaskPayloadCodec Bean 优先于 serializer 适配器")
    void customTaskPayloadCodec_isPreferredOverSerializerAdapter() {
        TaskPayloadCodec customCodec = new TaskPayloadCodec() {
            @Override
            public String encode(Object payload, TaskPayloadCodecContext context) {
                return "codec:" + payload;
            }

            @Override
            public <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context) {
                return targetType.cast(payload.replace("codec:", ""));
            }
        };

        contextRunner
                .withBean(TaskPayloadCodec.class, () -> customCodec)
                .run(context -> {
                    TaskPayloadCodec codec = context.getBean(TaskPayloadCodec.class);

                    assertThat(codec).isSameAs(customCodec);
                    assertThat(codec.encode("payload", TaskPayloadCodecContext.encode(String.class)))
                            .isEqualTo("codec:payload");
                });
    }

    @Test
    @DisplayName("自定义 TaskTraceIdGenerator Bean 时自动配置不覆盖")
    void customTaskTraceIdGenerator_isNotOverridden() {
        TaskTraceIdGenerator customGenerator = request -> "custom-trace";

        contextRunner
                .withBean(TaskTraceIdGenerator.class, () -> customGenerator)
                .run(context ->
                        assertThat(context).getBean(TaskTraceIdGenerator.class).isSameAs(customGenerator));
    }

    @Test
    @DisplayName("自定义 TaskNameResolver Bean 时自动配置不覆盖")
    void customTaskNameResolver_isNotOverridden() {
        TaskNameResolver customResolver = (handler, handlerClass, annotation) -> "custom-name";

        contextRunner
                .withBean(TaskNameResolver.class, () -> customResolver)
                .run(context ->
                        assertThat(context).getBean(TaskNameResolver.class).isSameAs(customResolver));
    }

    @Test
    @DisplayName("自定义 FailureClassifier Bean 时自动配置不覆盖")
    void customFailureClassifier_isNotOverridden() {
        FailureClassifier customClassifier = (task, error) -> FailureDecision.dead("custom");

        contextRunner
                .withBean(FailureClassifier.class, () -> customClassifier)
                .run(context ->
                        assertThat(context).getBean(FailureClassifier.class).isSameAs(customClassifier));
    }

    @Test
    @DisplayName("自定义 RetryStrategy Bean 会注册到重试策略 registry")
    void customRetryStrategyBean_isRegisteredInRegistry() {
        contextRunner
                .withUserConfiguration(CustomRetryStrategyConfiguration.class)
                .run(context -> {
                    RetryStrategy customStrategy = context.getBean(CustomRetryStrategy.class);
                    assertThat(context).getBean(RetryStrategyRegistry.class)
                            .satisfies(registry ->
                                    assertThat(registry.getStrategy(RetryStrategyType.CUSTOM))
                                            .isSameAs(customStrategy));
                });
    }

    @Test
    @DisplayName("自定义 TaskEventListener Bean 会注册到事件发布器")
    void customTaskEventListenerBean_isRegisteredInPublisher() {
        contextRunner
                .withUserConfiguration(CustomTaskEventListenerConfiguration.class)
                .run(context -> {
                    RecordingTaskEventListener listener = context.getBean(RecordingTaskEventListener.class);
                    context.getBean(TaskEventPublisher.class)
                            .publish(com.reliabletask.core.model.TaskEvent.builder().taskId(1L).build());

                    assertThat(listener.events).hasSize(1);
                });
    }

    @Test
    @DisplayName("自定义 TaskInterceptor Bean 按 @Order 接入 chain")
    void customTaskInterceptorBeans_areOrderedInChain() {
        contextRunner
                .withUserConfiguration(CustomTaskInterceptorConfiguration.class)
                .run(context -> {
                    TaskInterceptorChain chain = context.getBean(TaskInterceptorChain.class);
                    java.util.List<Class<?>> interceptorTypes = chain.getInterceptors().stream()
                            .map(Object::getClass)
                            .toList();

                    assertThat(interceptorTypes)
                            .containsSubsequence(
                                    TraceTaskInterceptor.class,
                                    FirstTaskInterceptor.class,
                                    SecondTaskInterceptor.class);
                });
    }

    @Test
    @DisplayName("自定义 TaskDeadLetterHandler Bean 会注册到死信分发器")
    void customTaskDeadLetterHandlerBean_isRegisteredInDispatcher() {
        contextRunner
                .withUserConfiguration(CustomTaskDeadLetterHandlerConfiguration.class)
                .run(context -> {
                    RecordingTaskDeadLetterHandler handler =
                            context.getBean(RecordingTaskDeadLetterHandler.class);

                    context.getBean(TaskDeadLetterDispatcher.class)
                            .dispatch(DeadLetterContext.builder()
                                    .task(TaskInstance.builder().id(1L).build())
                                    .source("test")
                                    .build());

                    assertThat(context).doesNotHaveBean(NoopTaskDeadLetterHandler.class);
                    assertThat(handler.contexts).hasSize(1);
                    assertThat(handler.contexts.get(0).getTask().getId()).isEqualTo(1L);
                });
    }

    @Test
    @DisplayName("metrics 启用且存在 MeterRegistry 时装配 Micrometer recorder")
    void metricsEnabledWithMeterRegistry_registersMicrometerRecorder() {
        contextRunner
                .withPropertyValues("reliable-task.metrics.enabled=true")
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).getBean(TaskMetricsRecorder.class)
                            .isInstanceOf(MicrometerTaskMetricsRecorder.class);
                    assertThat(context).hasSingleBean(MicrometerTaskEventListener.class);
                });
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
    @DisplayName("Worker-only starter 不注册 Admin controller")
    void workerOnlyStarter_doesNotRegisterAdminController() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.auth.enabled=false")
                .run(context -> assertThat(context.containsBean("taskAdminController")).isFalse());
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
    @DisplayName("TaskHandler metadata 随注册写入 registry")
    void annotatedTaskHandlerBean_registersMetadata() {
        contextRunner
                .withUserConfiguration(AnnotatedTaskHandlerTestConfiguration.class)
                .run(context -> {
                    TaskHandlerRegistry registry = context.getBean(TaskHandlerRegistry.class);
                    TaskHandlerMetadata metadata = registry.getMetadata("CREATE_SHIPMENT");

                    assertThat(metadata.getTaskType()).isEqualTo("CREATE_SHIPMENT");
                    assertThat(metadata.getHandlerClassName()).isEqualTo(TestCreateShipmentHandler.class.getName());
                    assertThat(metadata.getPayloadType()).isEqualTo(String.class);
                    assertThat(metadata.getMaxConcurrency()).isEqualTo(2);
                    assertThat(metadata.getTimeoutMs()).isEqualTo(3000L);
                });
    }

    @Test
    @DisplayName("自定义 TaskNameResolver 参与 Handler 注册")
    void customTaskNameResolver_isUsedForHandlerRegistration() {
        contextRunner
                .withUserConfiguration(CustomTaskNameResolverConfiguration.class)
                .run(context -> {
                    TaskHandlerRegistry registry = context.getBean(TaskHandlerRegistry.class);

                    assertThat(registry.hasHandler("RESOLVED_ResolverNamedHandler")).isTrue();
                    assertThat(registry.getMetadata("RESOLVED_ResolverNamedHandler").getTaskType())
                            .isEqualTo("RESOLVED_ResolverNamedHandler");
                });
    }

    @Test
    @DisplayName("@TaskHandler 与 getTaskType 不一致时启动失败")
    void mismatchedTaskHandlerAnnotation_failsContextStartup() {
        contextRunner
                .withUserConfiguration(MismatchedTaskHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("@TaskHandler value must match getTaskType()");
                });
    }

    @Test
    @DisplayName("重复 taskType 时启动失败")
    void duplicateTaskHandlerTaskType_failsContextStartup() {
        contextRunner
                .withUserConfiguration(DuplicateTaskHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Duplicate TaskHandler for taskType: DUPLICATE_TASK");
                });
    }

    @Test
    @DisplayName("与 Spring Boot 默认 applicationTaskExecutor Bean 共存")
    void springBootTaskExecutorBean_canCoexistWithReliableTaskExecutor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TaskExecutionAutoConfiguration.class,
                        ReliableTaskAutoConfiguration.class,
                        ReliableTaskExecutorAutoConfiguration.class))
                .withUserConfiguration(TaskStoreTestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("applicationTaskExecutor");
                    assertThat(context).hasSingleBean(org.springframework.core.task.AsyncTaskExecutor.class);
                    assertThat(context).hasBean("reliableTaskExecutor");
                    assertThat(context).hasSingleBean(TaskExecutor.class);
                });
    }

    @Test
    @DisplayName("V2 配置默认值保持兼容")
    void v2ConfigurationDefaults_preserveV15Behavior() {
        contextRunner.run(context -> {
            ReliableTaskProperties properties = context.getBean(ReliableTaskProperties.class);

            assertThat(properties.getMetrics().isEnabled()).isFalse();
            assertThat(properties.getMetrics().isIncludeWorkerIdTag()).isFalse();
            assertThat(properties.getMetrics().getStatsCacheTtlMs()).isEqualTo(5000L);
            assertThat(properties.getAlert().isEnabled()).isFalse();
            assertThat(properties.getAlert().getPendingThreshold()).isEqualTo(0L);
            assertThat(properties.getAlert().getFailureRateThreshold()).isEqualTo(1.0D);
            assertThat(properties.getAlert().getWindowSeconds()).isEqualTo(300L);
            assertThat(properties.getIdempotency().getStrategy()).isEqualTo("STRICT_UNIQUE");
            assertThat(properties.getRetry().getExponentialMultiplier()).isEqualTo(2.0D);
            assertThat(properties.getRetry().getJitterRatio()).isEqualTo(0.0D);
            assertThat(properties.getRetry().getMinDelayMs()).isEqualTo(0L);
            assertThat(properties.getRetry().getMaxDelayMs()).isEqualTo(300000L);
            assertThat(properties.getExecutor().getMode()).isEqualTo(ThreadPoolProperties.ExecutionMode.PLATFORM);
            assertThat(properties.getSerializer().getType()).isEqualTo("JACKSON");
            assertThat(properties.getStore().getTablePrefix()).isEmpty();
            assertThat(properties.getWorker().getBackpressure().isEnabled()).isFalse();
            assertThat(properties.getWorker().getMaxBatchSize()).isEqualTo(1000);
            assertThat(properties.getWorker().getBackpressure().getMinFetchSize()).isEqualTo(1);
            assertThat(properties.getWorker().getBackpressure().getMaxFetchSize()).isEqualTo(10);
            assertThat(properties.getWorker().getHeartbeat().isEnabled()).isFalse();
            assertThat(properties.getWorker().getHeartbeat().getIntervalMs()).isEqualTo(10000L);
            assertThat(properties.getWorker().getHeartbeat().getLockRenewalTtlSeconds()).isEqualTo(300L);
            assertThat(properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds()).isEqualTo(60L);
            assertThat(properties.getAdmin().isEnabled()).isFalse();
            assertThat(properties.getAdmin().isWriteEnabled()).isFalse();
            assertThat(properties.getAdmin().getPort()).isEqualTo(9090);
            assertThat(properties.getAdmin().getContextPath()).isEqualTo("/reliable-task");
            assertThat(properties.getAdmin().getQuery().getDefaultWindowHours()).isEqualTo(24);
            assertThat(properties.getAdmin().getQuery().getMaxWindowDays()).isEqualTo(30);
            assertThat(properties.getAdmin().getQuery().getDefaultLimit()).isEqualTo(50);
            assertThat(properties.getAdmin().getQuery().getMaxLimit()).isEqualTo(200);
            assertThat(properties.getAdmin().getQuery().getSlowThresholdMs()).isEqualTo(30_000L);
            assertThat(properties.getAdmin().getAudit().isEnabled()).isFalse();
            assertThat(properties.getAdmin().getAuth().isEnabled()).isTrue();
            assertThat(properties.getAdmin().getBatch().isEnabled()).isFalse();
        });
    }

    @Test
    @DisplayName("V2 配置可通过 reliable-task 前缀绑定")
    void v2Configuration_canBindFromProperties() {
        contextRunner
                .withPropertyValues(
                        "reliable-task.metrics.enabled=true",
                        "reliable-task.metrics.include-worker-id-tag=true",
                        "reliable-task.metrics.stats-cache-ttl-ms=15000",
                        "reliable-task.alert.enabled=true",
                        "reliable-task.alert.pending-threshold=10",
                        "reliable-task.alert.failure-rate-threshold=0.7",
                        "reliable-task.alert.window-seconds=120",
                        "reliable-task.idempotency.strategy=ALLOW_AFTER_TERMINAL",
                        "reliable-task.retry.exponential-multiplier=1.5",
                        "reliable-task.retry.jitter-ratio=0.25",
                        "reliable-task.retry.min-delay-ms=250",
                        "reliable-task.retry.max-delay-ms=120000",
                        "reliable-task.executor.mode=virtual",
                        "reliable-task.serializer.type=CUSTOM",
                        "reliable-task.store.table-prefix=rt_",
                        "reliable-task.worker.backpressure.enabled=true",
                        "reliable-task.worker.max-batch-size=250",
                        "reliable-task.worker.backpressure.min-fetch-size=2",
                        "reliable-task.worker.backpressure.max-fetch-size=6",
                        "reliable-task.worker.heartbeat.enabled=true",
                        "reliable-task.worker.heartbeat.interval-ms=2000",
                        "reliable-task.worker.heartbeat.lock-renewal-ttl-seconds=120",
                        "reliable-task.worker.heartbeat.stale-worker-threshold-seconds=30",
                        "reliable-task.recovery.interval-ms=15000",
                        "reliable-task.recovery.timeout-seconds=90",
                        "reliable-task.recovery.max-reset-per-scan=25",
                        "reliable-task.admin.enabled=true",
                        "reliable-task.admin.write-enabled=true",
                        "reliable-task.admin.port=9191",
                        "reliable-task.admin.context-path=/ops/reliable-task",
                        "reliable-task.admin.max-page-size=120",
                        "reliable-task.admin.max-batch-limit=400",
                        "reliable-task.admin.query.default-window-hours=12",
                        "reliable-task.admin.query.max-window-days=14",
                        "reliable-task.admin.query.default-limit=25",
                        "reliable-task.admin.query.max-limit=80",
                        "reliable-task.admin.query.slow-threshold-ms=45000",
                        "reliable-task.admin.audit.enabled=true",
                        "reliable-task.admin.auth.enabled=true",
                        "reliable-task.admin.batch.enabled=true"
                )
                .run(context -> {
                    ReliableTaskProperties properties = context.getBean(ReliableTaskProperties.class);

                    assertThat(properties.getMetrics().isEnabled()).isTrue();
                    assertThat(properties.getMetrics().isIncludeWorkerIdTag()).isTrue();
                    assertThat(properties.getMetrics().getStatsCacheTtlMs()).isEqualTo(15000L);
                    assertThat(properties.getAlert().isEnabled()).isTrue();
                    assertThat(properties.getAlert().getPendingThreshold()).isEqualTo(10L);
                    assertThat(properties.getAlert().getFailureRateThreshold()).isEqualTo(0.7D);
                    assertThat(properties.getAlert().getWindowSeconds()).isEqualTo(120L);
                    assertThat(properties.getIdempotency().getStrategy()).isEqualTo("ALLOW_AFTER_TERMINAL");
                    assertThat(properties.getRetry().getExponentialMultiplier()).isEqualTo(1.5D);
                    assertThat(properties.getRetry().getJitterRatio()).isEqualTo(0.25D);
                    assertThat(properties.getRetry().getMinDelayMs()).isEqualTo(250L);
                    assertThat(properties.getRetry().getMaxDelayMs()).isEqualTo(120000L);
                    assertThat(properties.getExecutor().getMode()).isEqualTo(ThreadPoolProperties.ExecutionMode.VIRTUAL);
                    assertThat(context.getBean(ThreadPoolProperties.class).getMode())
                            .isEqualTo(ThreadPoolProperties.ExecutionMode.VIRTUAL);
                    assertThat(properties.getSerializer().getType()).isEqualTo("CUSTOM");
                    assertThat(properties.getStore().getTablePrefix()).isEqualTo("rt_");
                    assertThat(properties.getWorker().getBackpressure().isEnabled()).isTrue();
                    assertThat(properties.getWorker().getMaxBatchSize()).isEqualTo(250);
                    assertThat(properties.getWorker().getBackpressure().getMinFetchSize()).isEqualTo(2);
                    assertThat(properties.getWorker().getBackpressure().getMaxFetchSize()).isEqualTo(6);
                    assertThat(properties.getWorker().getHeartbeat().isEnabled()).isTrue();
                    assertThat(properties.getWorker().getHeartbeat().getIntervalMs()).isEqualTo(2000L);
                    assertThat(properties.getWorker().getHeartbeat().getLockRenewalTtlSeconds()).isEqualTo(120L);
                    assertThat(properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds()).isEqualTo(30L);
                    assertThat(properties.getRecovery().getIntervalMs()).isEqualTo(15000L);
                    assertThat(properties.getRecovery().getTimeoutSeconds()).isEqualTo(90L);
                    assertThat(properties.getRecovery().getMaxResetPerScan()).isEqualTo(25);
                    assertThat(properties.getAdmin().isEnabled()).isTrue();
                    assertThat(properties.getAdmin().isWriteEnabled()).isTrue();
                    assertThat(properties.getAdmin().getPort()).isEqualTo(9191);
                    assertThat(properties.getAdmin().getContextPath()).isEqualTo("/ops/reliable-task");
                    assertThat(properties.getAdmin().getMaxPageSize()).isEqualTo(120);
                    assertThat(properties.getAdmin().getMaxBatchLimit()).isEqualTo(400);
                    assertThat(properties.getAdmin().getQuery().getDefaultWindowHours()).isEqualTo(12);
                    assertThat(properties.getAdmin().getQuery().getMaxWindowDays()).isEqualTo(14);
                    assertThat(properties.getAdmin().getQuery().getDefaultLimit()).isEqualTo(25);
                    assertThat(properties.getAdmin().getQuery().getMaxLimit()).isEqualTo(80);
                    assertThat(properties.getAdmin().getQuery().getSlowThresholdMs()).isEqualTo(45_000L);
                    assertThat(properties.getAdmin().getAudit().isEnabled()).isTrue();
                    assertThat(properties.getAdmin().getAuth().isEnabled()).isTrue();
                    assertThat(properties.getAdmin().getBatch().isEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TaskStoreTestConfiguration {
        @Bean
        TaskStore taskStore() {
            return stub(TaskStore.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0D;
            }
            return null;
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedTaskHandlerTestConfiguration {
        @Bean
        TestCreateShipmentHandler testCreateShipmentHandler() {
            return new TestCreateShipmentHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRetryStrategyConfiguration {
        @Bean
        CustomRetryStrategy customRetryStrategy() {
            return new CustomRetryStrategy();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTaskEventListenerConfiguration {
        @Bean
        RecordingTaskEventListener recordingTaskEventListener() {
            return new RecordingTaskEventListener();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTaskInterceptorConfiguration {
        @Bean
        @Order(20)
        SecondTaskInterceptor secondTaskInterceptor() {
            return new SecondTaskInterceptor();
        }

        @Bean
        @Order(10)
        FirstTaskInterceptor firstTaskInterceptor() {
            return new FirstTaskInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTaskDeadLetterHandlerConfiguration {
        @Bean
        RecordingTaskDeadLetterHandler recordingTaskDeadLetterHandler() {
            return new RecordingTaskDeadLetterHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTaskNameResolverConfiguration {
        @Bean
        TaskNameResolver customTaskNameResolver() {
            return (handler, handlerClass, annotation) -> "RESOLVED_" + handlerClass.getSimpleName();
        }

        @Bean
        ResolverNamedHandler resolverNamedHandler() {
            return new ResolverNamedHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MismatchedTaskHandlerConfiguration {
        @Bean
        MismatchedTaskHandler mismatchedTaskHandler() {
            return new MismatchedTaskHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateTaskHandlerConfiguration {
        @Bean
        FirstDuplicateTaskHandler firstDuplicateTaskHandler() {
            return new FirstDuplicateTaskHandler();
        }

        @Bean
        SecondDuplicateTaskHandler secondDuplicateTaskHandler() {
            return new SecondDuplicateTaskHandler();
        }
    }

    static class CustomRetryStrategy implements RetryStrategy {
        @Override
        public RetryStrategyType getType() {
            return RetryStrategyType.CUSTOM;
        }

        @Override
        public long nextDelayMs(int retryCount, long intervalMs, long maxDelayMs) {
            return 123L;
        }
    }

    static class RecordingTaskEventListener implements TaskEventListener {
        private final java.util.List<com.reliabletask.core.model.TaskEvent> events = new java.util.ArrayList<>();

        @Override
        public void onEvent(com.reliabletask.core.model.TaskEvent event) {
            events.add(event);
        }
    }

    static class FirstTaskInterceptor implements TaskInterceptor {
        @Override
        public void beforeExecute(TaskExecutionContext context) {
            // 测试只验证自动配置排序。
        }
    }

    static class SecondTaskInterceptor implements TaskInterceptor {
        @Override
        public void beforeExecute(TaskExecutionContext context) {
            // 测试只验证自动配置排序。
        }
    }

    static class RecordingTaskDeadLetterHandler implements TaskDeadLetterHandler {
        private final java.util.List<DeadLetterContext> contexts = new java.util.ArrayList<>();

        @Override
        public void handle(DeadLetterContext context) {
            contexts.add(context);
        }
    }

    @com.reliabletask.core.annotation.TaskHandler("CREATE_SHIPMENT")
    static class TestCreateShipmentHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "CREATE_SHIPMENT";
        }

        @Override
        public int maxConcurrency() {
            return 2;
        }

        @Override
        public long timeoutMs() {
            return 3000L;
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证注册行为，不执行任务。
        }
    }

    static class ResolverNamedHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "ORIGINAL_NAME";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证注册行为，不执行任务。
        }
    }

    @com.reliabletask.core.annotation.TaskHandler("ANNOTATED_NAME")
    static class MismatchedTaskHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "METHOD_NAME";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证启动失败。
        }
    }

    static class FirstDuplicateTaskHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "DUPLICATE_TASK";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证启动失败。
        }
    }

    static class SecondDuplicateTaskHandler implements com.reliabletask.core.spi.TaskHandler {
        @Override
        public String getTaskType() {
            return "DUPLICATE_TASK";
        }

        @Override
        public void execute(TaskInstance task) {
            // 测试只验证启动失败。
        }
    }
}
