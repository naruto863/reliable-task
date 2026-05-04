package com.reliabletask.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务处理器注解
 *
 * <p>标注在实现 com.reliabletask.core.spi.TaskHandler 接口的类上，
 * 声明该 Handler 处理的任务类型。
 *
 * <p>使用示例:
 * <pre>
 * &#64;Component
 * &#64;TaskHandler("CREATE_SHIPMENT")
 * public class CreateShipmentHandler implements TaskHandler {
 *     public void execute(TaskInstance task) {
 *         // 处理发货逻辑
 *     }
 * }
 * </pre>
 *
 * <p>core 模块不依赖 Spring。业务方在 Spring Boot 场景下应同时将 Handler
 * 声明为 Spring Bean，例如使用 @Component 或 @Bean。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskHandler {

    /**
     * 任务类型标识
     *
     * <p>必须全局唯一，与 TaskInstance.taskType 匹配。
     * 框架根据此值将任务路由到对应的 Handler。
     *
     * @return 任务类型字符串
     */
    String value();
}
