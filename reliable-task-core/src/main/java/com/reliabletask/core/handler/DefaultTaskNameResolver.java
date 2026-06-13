package com.reliabletask.core.handler;

import com.reliabletask.core.spi.TaskHandler;
import com.reliabletask.core.spi.TaskNameResolver;

/**
 * 默认 TaskHandler 名称解析器。
 *
 * <p>默认策略保持现有兼容行为：{@code @TaskHandler} 注解存在时必须与
 * {@link TaskHandler#getTaskType()} 一致；注解缺失时直接使用 Handler 声明的 taskType。
 */
public class DefaultTaskNameResolver implements TaskNameResolver {

    @Override
    public String resolve(TaskHandler handler, Class<?> handlerClass,
                          com.reliabletask.core.annotation.TaskHandler annotation) {
        String taskType = handler.getTaskType();
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalStateException("TaskHandler taskType must not be blank: class="
                    + handlerClass.getName());
        }
        if (annotation != null && !annotation.value().equals(taskType)) {
            throw new IllegalStateException("@TaskHandler value must match getTaskType(): class="
                    + handlerClass.getName()
                    + ", annotation=" + annotation.value()
                    + ", getTaskType=" + taskType);
        }
        return taskType;
    }
}
