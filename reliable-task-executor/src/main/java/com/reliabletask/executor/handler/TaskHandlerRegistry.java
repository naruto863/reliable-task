package com.reliabletask.executor.handler;

import com.reliabletask.core.spi.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
     * 手动注册 Handler（用于测试）
     */
    void registerHandler(TaskHandler handler) {
        String taskType = handler.getTaskType();
        TaskHandler existing = registry.putIfAbsent(taskType, handler);
        if (existing == null) {
            log.info("Registered TaskHandler: type={}, class={}",
                    taskType, handler.getClass().getSimpleName());
            return;
        }
        if (existing == handler) {
            log.debug("TaskHandler already registered: type={}, class={}",
                    taskType, handler.getClass().getSimpleName());
            return;
        }
        throw new IllegalStateException(
                "Duplicate TaskHandler for taskType: " + taskType
                        + ", existing=" + existing.getClass().getName()
                        + ", new=" + handler.getClass().getName());
    }
}
