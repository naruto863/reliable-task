package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskInstance;

/**
 * 任务处理器 SPI
 *
 * <p>业务方实现此接口以处理特定类型的异步任务。
 * 每个 TaskHandler 通过 getTaskType() 声明自己处理的任务类型，
 * Worker 消费任务时根据 taskType 路由到对应的 Handler。
 *
 * <p>实现要求:
 * <ul>
 *   <li>实现类必须是线程安全的（同一 Handler 实例可能被多个线程并发调用）</li>
 *   <li>execute() 方法抛出异常视为执行失败，框架会自动处理重试逻辑</li>
 *   <li>execute() 正常返回视为执行成功，框架会更新任务状态为 SUCCESS</li>
 * </ul>
 *
 * <p>使用示例:
 * <pre>
 * public class CreateShipmentHandler implements TaskHandler {
 *     public String getTaskType() { return "CREATE_SHIPMENT"; }
 *     public void execute(TaskInstance task) {
 *         // 解析 payload，调用外部发货接口
 *     }
 * }
 * </pre>
 */
public interface TaskHandler {

    /**
     * 声明此 Handler 处理的任务类型
     *
     * <p>返回值必须全局唯一，与 TaskInstance.taskType 匹配。
     * 框架根据此值将任务路由到正确的 Handler。
     *
     * @return 任务类型标识，不可为 null
     */
    String getTaskType();

    /**
     * 执行任务
     *
     * <p>框架会在此方法调用前后自动更新任务状态:
     * <pre>
     *   调用前: PENDING/RETRYING → RUNNING
     *   正常返回: RUNNING → SUCCESS
     *   抛出异常: RUNNING → RETRYING (可重试) 或 DEAD (不可重试/耗尽)
     * </pre>
     *
     * <p>实现注意事项:
     * <ul>
     *   <li>此方法由 Worker 线程池调用，必须保证线程安全</li>
     *   <li>方法抛出任何异常都会被框架捕获并进入重试流程</li>
     *   <li>如果需要标记任务不可重试，抛出 NonRetryableException</li>
     *   <li>payload 反序列化由框架自动完成，Handler 直接通过 task.getPayload() 获取</li>
     * </ul>
     *
     * @param task 当前任务实例，包含 payload、bizId 等完整上下文
     * @throws Exception 执行失败时抛出异常，触发重试或进入 DEAD
     */
    void execute(TaskInstance task) throws Exception;

    /**
     * 声明 payload 目标类型。
     *
     * <p>默认返回 String，保持 V1.5 兼容。需要类型化 payload 的 Handler
     * 可覆盖此方法，并实现 {@link #execute(TaskInstance, Object)}。
     *
     * @return payload 目标类型
     */
    default Class<?> payloadType() {
        return String.class;
    }

    /**
     * 使用类型化 payload 执行任务。
     *
     * <p>默认委托到旧的 execute(TaskInstance)，旧 Handler 无需改动。
     *
     * @param task    当前任务实例
     * @param payload 反序列化后的 payload
     * @throws Exception 执行失败时抛出异常
     */
    default void execute(TaskInstance task, Object payload) throws Exception {
        execute(task);
    }

    /**
     * 获取最大并发数
     *
     * <p>返回此 Handler 允许的最大并发执行数。
     * 框架会根据此值对同类型任务的并发执行进行限流。
     *
     * <p>默认返回 0 表示不限制并发数。
     *
     * @return 最大并发数，0 表示不限制
     */
    default int maxConcurrency() {
        return 0;
    }

    /**
     * 获取执行超时时间（毫秒）
     *
     * <p>返回此 Handler 的单次执行超时时间。
     * 超过此时间未完成的任务会被框架中断并标记为失败。
     *
     * <p>默认返回 60000 (60 秒)。
     *
     * @return 超时时间，单位毫秒
     */
    default long timeoutMs() {
        return 60_000L;
    }
}
