package com.reliabletask.executor.handler;

import com.reliabletask.core.annotation.TaskHandler;
import com.reliabletask.core.handler.DefaultTaskNameResolver;
import com.reliabletask.core.spi.TaskNameResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Map;

/**
 * 任务处理器自动注册器
 *
 * <p>在 Spring 容器刷新完成后，自动收集所有标注了 @TaskHandler 注解的 Spring Bean，
 * 并注册到 TaskHandlerRegistry 中。
 *
 * <p>工作流程:
 * <pre>
 *   Spring 容器启动 → 组件扫描发现 Handler Bean
 *   → ContextRefreshedEvent 触发 → 收集所有 TaskHandler Bean
 *   → 逐个注册到 TaskHandlerRegistry → 重复检测 → 启动日志输出
 * </pre>
 *
 * <p>业务方使用方式:
 * <pre>
 * &#64;Component
 * &#64;TaskHandler("CREATE_SHIPMENT")
 * public class CreateShipmentHandler implements com.reliabletask.core.spi.TaskHandler {
 *     public void execute(TaskInstance task) { ... }
 * }
 * </pre>
 * Handler 必须先通过 @Component 或 @Bean 成为 Spring Bean。
 *
 * <p>这里通过 {@link AopUtils#getTargetClass(Object)} 获取代理背后的真实类型，
 * 否则带有事务、切面或代理增强的 Handler 可能读不到类上的注解。
 */
@Slf4j
public class TaskHandlerAutoRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final TaskHandlerRegistry registry;
    private final TaskNameResolver taskNameResolver;

    public TaskHandlerAutoRegistrar(TaskHandlerRegistry registry) {
        this(registry, new DefaultTaskNameResolver());
    }

    public TaskHandlerAutoRegistrar(TaskHandlerRegistry registry, TaskNameResolver taskNameResolver) {
        this.registry = registry;
        this.taskNameResolver = taskNameResolver;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        Map<String, com.reliabletask.core.spi.TaskHandler> handlers =
                context.getBeansOfType(com.reliabletask.core.spi.TaskHandler.class);

        if (handlers.isEmpty()) {
            log.info("No TaskHandler beans found in context");
            return;
        }

        int registeredCount = 0;
        for (Map.Entry<String, com.reliabletask.core.spi.TaskHandler> entry : handlers.entrySet()) {
            String beanName = entry.getKey();
            com.reliabletask.core.spi.TaskHandler handler = entry.getValue();

            Class<?> handlerClass = AopUtils.getTargetClass(handler);
            TaskHandler annotation = AnnotationUtils.findAnnotation(handlerClass, TaskHandler.class);
            // 名称解析集中委托给 TaskNameResolver，便于未来支持自定义命名规范或兼容迁移策略。
            String taskType = taskNameResolver.resolve(handler, handlerClass, annotation);
            try {
                registry.registerHandler(taskType, handler, handlerClass, null);
                registeredCount++;
                log.info("Auto-registered TaskHandler: beanName={}, taskType={}, class={}",
                        beanName, taskType, handlerClass.getSimpleName());
            } catch (IllegalStateException e) {
                log.error("Failed to register TaskHandler: beanName={}, taskType={}, reason={}",
                        beanName, taskType, e.getMessage());
                throw e;
            }
        }

        log.info("TaskHandlerAutoRegistrar completed: {} handlers registered", registeredCount);
    }
}
