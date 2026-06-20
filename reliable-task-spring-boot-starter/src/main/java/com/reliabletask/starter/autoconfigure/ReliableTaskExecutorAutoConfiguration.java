package com.reliabletask.starter.autoconfigure;

import com.reliabletask.core.spi.AlarmNotifier;
import com.reliabletask.core.diagnostics.DefaultTaskExceptionFormatter;
import com.reliabletask.core.diagnostics.TaskExceptionFormatter;
import com.reliabletask.core.classifier.DefaultFailureClassifier;
import com.reliabletask.core.deadletter.TaskDeadLetterDispatcher;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.spi.FailureClassifier;
import com.reliabletask.core.spi.TaskDeadLetterHandler;
import com.reliabletask.core.spi.TaskCommandStore;
import com.reliabletask.core.spi.TaskEventListener;
import com.reliabletask.core.spi.TaskOperationsStore;
import com.reliabletask.core.spi.TaskQueryStore;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.core.spi.IdempotencyStrategy;
import com.reliabletask.core.spi.TaskAuditRecorder;
import com.reliabletask.core.spi.TaskMetricsRecorder;
import com.reliabletask.core.spi.TaskPayloadCodec;
import com.reliabletask.core.spi.TaskPayloadCodecAdapters;
import com.reliabletask.core.spi.TaskPayloadSerializer;
import com.reliabletask.core.spi.TaskNameResolver;
import com.reliabletask.core.spi.TaskTraceIdGenerator;
import com.reliabletask.core.spi.RetryStrategy;
import com.reliabletask.core.spi.WorkerHeartbeatReporter;
import com.reliabletask.core.spi.noop.NoopAlarmNotifier;
import com.reliabletask.core.spi.noop.NoopTaskAuditRecorder;
import com.reliabletask.core.spi.noop.NoopTaskDeadLetterHandler;
import com.reliabletask.core.spi.noop.NoopTaskMetricsRecorder;
import com.reliabletask.core.spi.noop.NoopWorkerHeartbeatReporter;
import com.reliabletask.executor.alert.AlertProperties;
import com.reliabletask.executor.alert.DefaultTaskAlertService;
import com.reliabletask.executor.alert.NoopTaskAlertService;
import com.reliabletask.executor.alert.TaskAlertScheduler;
import com.reliabletask.executor.alert.TaskAlertService;
import com.reliabletask.executor.handler.TaskExecutor;
import com.reliabletask.executor.handler.TaskHandlerAutoRegistrar;
import com.reliabletask.executor.handler.TaskHandlerRegistry;
import com.reliabletask.executor.interceptor.TaskInterceptor;
import com.reliabletask.executor.interceptor.TaskInterceptorChain;
import com.reliabletask.executor.interceptor.TraceTaskInterceptor;
import com.reliabletask.executor.recovery.RecoveryProperties;
import com.reliabletask.executor.retry.RetryEngine;
import com.reliabletask.executor.retry.RetryProperties;
import com.reliabletask.executor.recovery.TaskRecoveryScheduler;
import com.reliabletask.executor.retry.RetryStrategyResolver;
import com.reliabletask.executor.serializer.JacksonTaskPayloadSerializer;
import com.reliabletask.executor.template.TransactionAwareTaskTemplate;
import com.reliabletask.executor.threadpool.TaskExecutorFactory;
import com.reliabletask.executor.threadpool.ThreadPoolProperties;
import com.reliabletask.executor.worker.WorkerIdGenerator;
import com.reliabletask.executor.worker.WorkerProperties;
import com.reliabletask.executor.worker.WorkerScheduler;
import com.reliabletask.starter.metrics.MicrometerTaskMetricsRecorder;
import com.reliabletask.starter.metrics.MicrometerTaskEventListener;
import com.reliabletask.starter.config.ReliableTaskProperties;
import com.reliabletask.core.strategy.AllowAfterTerminalIdempotencyStrategy;
import com.reliabletask.core.strategy.ExponentialRetryStrategy;
import com.reliabletask.core.strategy.FixedRetryStrategy;
import com.reliabletask.core.strategy.RetryStrategyRegistry;
import com.reliabletask.core.strategy.StrictUniqueIdempotencyStrategy;
import com.reliabletask.core.handler.DefaultTaskNameResolver;
import com.reliabletask.core.trace.DefaultTaskTraceIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行层自动配置类
 *
 * <p>注册 Worker、线程池、重试引擎、任务模板等执行相关 Bean。
 * 通过 reliable-task.enabled 控制总开关。
 */
@AutoConfiguration(after = {ReliableTaskAutoConfiguration.class, ReliableTaskStoreAutoConfiguration.class})
@ConditionalOnProperty(prefix = "reliable-task", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({TaskExecutor.class, WorkerScheduler.class, TaskTemplate.class})
@EnableScheduling
public class ReliableTaskExecutorAutoConfiguration {

    // ==================== Worker 配置 ====================

    @Bean
    @ConditionalOnMissingBean(WorkerProperties.class)
    public WorkerProperties workerProperties(ReliableTaskProperties properties) {
        ReliableTaskProperties.Worker wp = properties.getWorker();
        WorkerProperties workerProps = new WorkerProperties();
        workerProps.setEnabled(wp.isEnabled());
        workerProps.setPollIntervalMs(wp.getPollIntervalMs());
        workerProps.setBatchSize(wp.getBatchSize());
        workerProps.setMaxBatchSize(wp.getMaxBatchSize());
        workerProps.setLockTtlSeconds(wp.getLockTtlSeconds());
        workerProps.setBackpressureEnabled(wp.getBackpressure().isEnabled());
        workerProps.setBackpressureMinFetchSize(wp.getBackpressure().getMinFetchSize());
        workerProps.setBackpressureMaxFetchSize(wp.getBackpressure().getMaxFetchSize());
        workerProps.setHeartbeatEnabled(wp.getHeartbeat().isEnabled());
        workerProps.setHeartbeatIntervalMs(wp.getHeartbeat().getIntervalMs());
        workerProps.setLockRenewalTtlSeconds(wp.getHeartbeat().getLockRenewalTtlSeconds());
        workerProps.setStaleWorkerThresholdSeconds(wp.getHeartbeat().getStaleWorkerThresholdSeconds());
        return workerProps;
    }

    @Bean
    @ConditionalOnMissingBean(WorkerScheduler.class)
    @ConditionalOnProperty(prefix = "reliable-task.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WorkerScheduler workerScheduler(TaskCommandStore taskCommandStore,
                                           TaskOperationsStore taskOperationsStore,
                                           WorkerProperties workerProperties,
                                           TaskExecutor taskExecutor,
                                           TaskEventPublisher eventPublisher) {
        return new WorkerScheduler(taskCommandStore, taskOperationsStore, workerProperties,
                taskExecutor, eventPublisher);
    }

    // ==================== 补偿恢复配置 ====================

    @Bean
    @ConditionalOnMissingBean(RecoveryProperties.class)
    public RecoveryProperties recoveryProperties(ReliableTaskProperties properties) {
        ReliableTaskProperties.Recovery rp = properties.getRecovery();
        RecoveryProperties recoveryProps = new RecoveryProperties();
        recoveryProps.setEnabled(rp.isEnabled());
        recoveryProps.setIntervalMs(rp.getIntervalMs());
        recoveryProps.setTimeoutSeconds(rp.getTimeoutSeconds());
        recoveryProps.setMaxResetPerScan(rp.getMaxResetPerScan());
        return recoveryProps;
    }

    @Bean
    @ConditionalOnMissingBean(TaskRecoveryScheduler.class)
    @ConditionalOnProperty(prefix = "reliable-task.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TaskRecoveryScheduler taskRecoveryScheduler(TaskCommandStore taskStore, RecoveryProperties recoveryProperties,
                                                       TaskEventPublisher eventPublisher) {
        return new TaskRecoveryScheduler(taskStore, recoveryProperties, eventPublisher);
    }

    // ==================== 线程池配置 ====================

    @Bean
    @ConditionalOnMissingBean(ThreadPoolProperties.class)
    public ThreadPoolProperties threadPoolProperties(ReliableTaskProperties properties) {
        ReliableTaskProperties.Executor ep = properties.getExecutor();
        ThreadPoolProperties poolProps = new ThreadPoolProperties();
        poolProps.setMode(ep.getMode());
        poolProps.setDefaultCoreSize(ep.getDefaultCoreSize());
        poolProps.setDefaultMaxSize(ep.getDefaultMaxSize());
        poolProps.setDefaultQueueCapacity(ep.getDefaultQueueCapacity());
        poolProps.setKeepAliveSeconds(ep.getKeepAliveSeconds());

        if (ep.getPools() != null && !ep.getPools().isEmpty()) {
            for (var entry : ep.getPools().entrySet()) {
                ReliableTaskProperties.Executor.PoolConfig pc = entry.getValue();
                poolProps.getPools().put(entry.getKey(),
                        new ThreadPoolProperties.PoolConfig(pc.getCoreSize(), pc.getMaxSize(), pc.getQueueCapacity()));
            }
        }
        return poolProps;
    }

    @Bean
    @ConditionalOnMissingBean(TaskExecutorFactory.class)
    public TaskExecutorFactory taskExecutorFactory(ThreadPoolProperties threadPoolProperties) {
        return new TaskExecutorFactory(threadPoolProperties);
    }

    // ==================== 重试引擎 ====================

    @Bean
    @ConditionalOnMissingBean(RetryProperties.class)
    public RetryProperties retryProperties(ReliableTaskProperties properties) {
        ReliableTaskProperties.Retry retry = properties.getRetry();
        RetryProperties retryProperties = new RetryProperties();
        retryProperties.setExponentialMultiplier(retry.getExponentialMultiplier());
        retryProperties.setJitterRatio(retry.getJitterRatio());
        retryProperties.setMinDelayMs(retry.getMinDelayMs());
        retryProperties.setMaxDelayMs(retry.getMaxDelayMs());
        return retryProperties;
    }

    @Bean
    @ConditionalOnMissingBean(RetryStrategyRegistry.class)
    public RetryStrategyRegistry retryStrategyRegistry(RetryProperties retryProperties,
                                                       List<RetryStrategy> retryStrategies) {
        return new RetryStrategyRegistry(
                new FixedRetryStrategy(),
                new ExponentialRetryStrategy(
                        retryProperties.getExponentialMultiplier(),
                        retryProperties.getJitterRatio()),
                retryStrategies);
    }

    @Bean
    @ConditionalOnMissingBean(RetryEngine.class)
    public RetryEngine retryEngine(TaskCommandStore taskStore,
                                   TaskMetricsRecorder metricsRecorder,
                                   TaskAuditRecorder auditRecorder,
                                   TaskAlertService alertService,
                                   TaskExceptionFormatter exceptionFormatter,
                                   RetryStrategyRegistry retryStrategyRegistry,
                                   RetryProperties retryProperties,
                                   FailureClassifier failureClassifier,
                                   TaskEventPublisher eventPublisher,
                                   TaskDeadLetterDispatcher deadLetterDispatcher) {
        return new RetryEngine(taskStore, metricsRecorder, auditRecorder, alertService, exceptionFormatter,
                retryStrategyRegistry, retryProperties, failureClassifier, eventPublisher, deadLetterDispatcher);
    }

    @Bean
    @ConditionalOnMissingBean(TaskExceptionFormatter.class)
    public TaskExceptionFormatter taskExceptionFormatter() {
        return new DefaultTaskExceptionFormatter();
    }

    @Bean
    @ConditionalOnMissingBean(FailureClassifier.class)
    public FailureClassifier failureClassifier() {
        return new DefaultFailureClassifier();
    }

    @Bean
    @ConditionalOnMissingBean(TaskDeadLetterHandler.class)
    public TaskDeadLetterHandler taskDeadLetterHandler() {
        return new NoopTaskDeadLetterHandler();
    }

    @Bean
    @ConditionalOnMissingBean(TaskDeadLetterDispatcher.class)
    public TaskDeadLetterDispatcher taskDeadLetterDispatcher(List<TaskDeadLetterHandler> handlers) {
        return new TaskDeadLetterDispatcher(handlers);
    }

    @Bean
    @ConditionalOnMissingBean(TaskEventPublisher.class)
    public TaskEventPublisher taskEventPublisher(List<TaskEventListener> listeners) {
        return new TaskEventPublisher(listeners);
    }

    // ==================== 告警配置 ====================

    @Bean
    @ConditionalOnMissingBean(AlertProperties.class)
    public AlertProperties alertProperties(ReliableTaskProperties properties) {
        ReliableTaskProperties.Alert ap = properties.getAlert();
        AlertProperties alertProperties = new AlertProperties();
        alertProperties.setEnabled(ap.isEnabled());
        alertProperties.setPendingThreshold(ap.getPendingThreshold());
        alertProperties.setFailureRateThreshold(ap.getFailureRateThreshold());
        alertProperties.setWindowSeconds(ap.getWindowSeconds());
        alertProperties.setScanIntervalMs(ap.getScanIntervalMs());
        return alertProperties;
    }

    @Bean
    @ConditionalOnMissingBean(AlarmNotifier.class)
    public AlarmNotifier alarmNotifier() {
        return new NoopAlarmNotifier();
    }

    @Bean
    @ConditionalOnMissingBean(TaskAlertService.class)
    @ConditionalOnProperty(prefix = "reliable-task.alert", name = "enabled", havingValue = "true")
    public TaskAlertService taskAlertService(AlarmNotifier alarmNotifier, AlertProperties alertProperties) {
        return new DefaultTaskAlertService(alarmNotifier, alertProperties);
    }

    @Bean
    @ConditionalOnMissingBean(TaskAlertService.class)
    public TaskAlertService noopTaskAlertService() {
        return new NoopTaskAlertService();
    }

    @Bean
    @ConditionalOnMissingBean(TaskAlertScheduler.class)
    @ConditionalOnProperty(prefix = "reliable-task.alert", name = "enabled", havingValue = "true")
    public TaskAlertScheduler taskAlertScheduler(TaskQueryStore taskStore,
                                                 AlertProperties alertProperties,
                                                 TaskAlertService alertService) {
        return new TaskAlertScheduler(taskStore, alertProperties, alertService);
    }

    // ==================== 任务执行器 ====================

    @Bean
    @ConditionalOnMissingBean(TaskPayloadSerializer.class)
    public TaskPayloadSerializer taskPayloadSerializer() {
        return new JacksonTaskPayloadSerializer();
    }

    @Bean
    @ConditionalOnMissingBean(TaskPayloadCodec.class)
    public TaskPayloadCodec taskPayloadCodec(TaskPayloadSerializer payloadSerializer) {
        return TaskPayloadCodecAdapters.fromSerializer(payloadSerializer);
    }

    @Bean
    @ConditionalOnMissingBean(TaskTraceIdGenerator.class)
    public TaskTraceIdGenerator taskTraceIdGenerator() {
        return new DefaultTaskTraceIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(TaskNameResolver.class)
    public TaskNameResolver taskNameResolver() {
        return new DefaultTaskNameResolver();
    }

    @Bean
    @ConditionalOnMissingBean(TraceTaskInterceptor.class)
    public TraceTaskInterceptor traceTaskInterceptor() {
        return new TraceTaskInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(TaskInterceptorChain.class)
    public TaskInterceptorChain taskInterceptorChain(List<TaskInterceptor> taskInterceptors) {
        List<TaskInterceptor> orderedInterceptors = new ArrayList<>(taskInterceptors);
        AnnotationAwareOrderComparator.sort(orderedInterceptors);
        return TaskInterceptorChain.of(orderedInterceptors);
    }

    @Bean
    @ConditionalOnMissingBean(TaskMetricsRecorder.class)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "reliable-task.metrics", name = "enabled", havingValue = "true")
    public TaskMetricsRecorder micrometerTaskMetricsRecorder(MeterRegistry meterRegistry,
                                                             TaskQueryStore taskStore,
                                                             TaskExecutorFactory executorFactory,
                                                             ReliableTaskProperties properties) {
        return new MicrometerTaskMetricsRecorder(meterRegistry, taskStore, executorFactory,
                properties.getMetrics());
    }

    @Bean
    @ConditionalOnMissingBean(MicrometerTaskEventListener.class)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "reliable-task.metrics", name = "enabled", havingValue = "true")
    public MicrometerTaskEventListener micrometerTaskEventListener(MeterRegistry meterRegistry) {
        return new MicrometerTaskEventListener(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(TaskMetricsRecorder.class)
    public TaskMetricsRecorder taskMetricsRecorder() {
        return new NoopTaskMetricsRecorder();
    }

    @Bean
    @ConditionalOnMissingBean(TaskAuditRecorder.class)
    public TaskAuditRecorder taskAuditRecorder() {
        return new NoopTaskAuditRecorder();
    }

    @Bean
    @ConditionalOnMissingBean(WorkerHeartbeatReporter.class)
    public WorkerHeartbeatReporter workerHeartbeatReporter() {
        return new NoopWorkerHeartbeatReporter();
    }

    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new TaskHandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(TaskHandlerAutoRegistrar.class)
    public TaskHandlerAutoRegistrar taskHandlerAutoRegistrar(TaskHandlerRegistry registry,
                                                            TaskNameResolver taskNameResolver) {
        return new TaskHandlerAutoRegistrar(registry, taskNameResolver);
    }

    @Bean
    @ConditionalOnMissingBean(TaskExecutor.class)
    public TaskExecutor reliableTaskExecutor(TaskCommandStore taskStore, TaskHandlerRegistry handlerRegistry,
                                             TaskExecutorFactory executorFactory, RetryEngine retryEngine,
                                             TaskInterceptorChain interceptorChain,
                                             TaskPayloadCodec payloadCodec,
                                             WorkerProperties workerProperties,
                                             TaskMetricsRecorder metricsRecorder,
                                             TaskAuditRecorder auditRecorder,
                                             TaskAlertService alertService,
                                             TaskEventPublisher eventPublisher,
                                             TaskDeadLetterDispatcher deadLetterDispatcher) {
        return new TaskExecutor(taskStore, handlerRegistry, executorFactory, retryEngine,
                interceptorChain, payloadCodec, workerProperties,
                metricsRecorder, auditRecorder, alertService, eventPublisher, deadLetterDispatcher);
    }

    // ==================== 任务模板 ====================

    @Bean
    @ConditionalOnMissingBean(name = "strictUniqueIdempotencyStrategy")
    public IdempotencyStrategy strictUniqueIdempotencyStrategy() {
        return new StrictUniqueIdempotencyStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(name = "allowAfterTerminalIdempotencyStrategy")
    public IdempotencyStrategy allowAfterTerminalIdempotencyStrategy() {
        return new AllowAfterTerminalIdempotencyStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(TaskTemplate.class)
    public TaskTemplate taskTemplate(TaskCommandStore taskStore,
                                     List<IdempotencyStrategy> idempotencyStrategies,
                                     ReliableTaskProperties properties,
                                     TaskPayloadCodec payloadCodec,
                                     TaskMetricsRecorder metricsRecorder,
                                     TaskEventPublisher eventPublisher,
                                     TaskTraceIdGenerator traceIdGenerator) {
        return new TransactionAwareTaskTemplate(taskStore, idempotencyStrategies,
                properties.getIdempotency().getStrategy(), payloadCodec, metricsRecorder, eventPublisher,
                traceIdGenerator);
    }
}
