package com.reliabletask.executor.handler;

import com.reliabletask.core.model.TaskHandlerMetadata;
import com.reliabletask.core.spi.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务处理器注册中心
 *
 * <p>Spring 启动时自动收集所有 TaskHandler Bean，
 * 按 getTaskType() 注册到内存 Map 中，供 TaskExecutor 路由使用。
 */
@Slf4j
@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> registry = new ConcurrentHashMap<>();
    private final Map<String, TaskHandlerMetadata> metadataRegistry = new ConcurrentHashMap<>();

    /**
     * 根据任务类型获取对应的 Handler
     *
     * @param taskType 任务类型
     * @return 对应的 TaskHandler
     * @throws IllegalArgumentException 如果未找到对应 Handler
     */
    public TaskHandler getHandler(String taskType) {
        TaskHandler handler = registry.get(taskType);
        if (handler == null) {
            throw new IllegalArgumentException("No TaskHandler registered for taskType: " + taskType);
        }
        return handler;
    }

    /**
     * 检查是否存在指定任务类型的 Handler
     */
    public boolean hasHandler(String taskType) {
        return registry.containsKey(taskType);
    }

    /**
     * 获取指定任务类型的 Handler 元数据。
     *
     * @param taskType 任务类型
     * @return Handler 元数据
     * @throws IllegalArgumentException 如果未找到对应元数据
     */
    public TaskHandlerMetadata getMetadata(String taskType) {
        TaskHandlerMetadata metadata = metadataRegistry.get(taskType);
        if (metadata == null) {
            throw new IllegalArgumentException("No TaskHandler metadata registered for taskType: " + taskType);
        }
        return metadata;
    }

    /**
     * 获取所有已注册 Handler 元数据快照。
     */
    public Map<String, TaskHandlerMetadata> getAllMetadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadataRegistry));
    }

    /**
     * 手动注册 Handler（用于测试）
     */
    void registerHandler(TaskHandler handler) {
        registerHandler(handler.getTaskType(), handler, handler.getClass(), null);
    }

    /**
     * 使用已解析 taskType 注册 Handler。
     */
    void registerHandler(String taskType, TaskHandler handler, Class<?> handlerClass, String description) {
        validateTaskType(taskType, handlerClass);
        TaskHandler existing = registry.putIfAbsent(taskType, handler);
        if (existing == null) {
            metadataRegistry.put(taskType, buildMetadata(taskType, handler, handlerClass, description));
            log.info("Registered TaskHandler: type={}, class={}",
                    taskType, handlerClass.getSimpleName());
            return;
        }
        if (existing == handler) {
            log.debug("TaskHandler already registered: type={}, class={}",
                    taskType, handlerClass.getSimpleName());
            return;
        }
        throw new IllegalStateException(
                "Duplicate TaskHandler for taskType: " + taskType
                        + ", existing=" + existing.getClass().getName()
                        + ", new=" + handler.getClass().getName());
    }

    private void validateTaskType(String taskType, Class<?> handlerClass) {
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalStateException("TaskHandler taskType must not be blank: class="
                    + handlerClass.getName());
        }
    }

    private TaskHandlerMetadata buildMetadata(String taskType, TaskHandler handler,
                                              Class<?> handlerClass, String description) {
        return TaskHandlerMetadata.builder()
                .taskType(taskType)
                .handlerClassName(handlerClass.getName())
                .payloadType(handler.payloadType())
                .maxConcurrency(handler.maxConcurrency())
                .timeoutMs(handler.timeoutMs())
                .description(description)
                .build();
    }
}
