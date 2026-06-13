package com.reliabletask.core.spi;

/**
 * TaskHandler 名称解析 SPI。
 *
 * <p>框架在 Handler 注册阶段调用该扩展点解析最终 taskType。默认实现保持
 * v0.5 行为：校验 {@code @TaskHandler} 注解值与 {@link TaskHandler#getTaskType()}
 * 一致；如果注解缺失，则使用 {@link TaskHandler#getTaskType()}。
 */
public interface TaskNameResolver {

    /**
     * 解析 Handler 的最终 taskType。
     *
     * @param handler      Handler 实例
     * @param handlerClass Handler 目标类，Spring AOP 场景下应传入 target class
     * @param annotation   Handler 类上的 {@code @TaskHandler} 注解，可为 null
     * @return 解析后的 taskType，不可为空白
     */
    String resolve(TaskHandler handler, Class<?> handlerClass,
                   com.reliabletask.core.annotation.TaskHandler annotation);
}
