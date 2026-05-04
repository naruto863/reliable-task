package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskInstance;

/**
 * 告警通知器 SPI
 *
 * <p>当任务进入需要人工关注的状态时（如 DEAD），
 * 框架会调用此接口通知业务方。
 * 业务方可实现此接口接入钉钉、企业微信、邮件、短信等告警渠道。
 *
 * <p>告警触发场景:
 * <ul>
 *   <li>任务进入 DEAD 状态（重试耗尽或不可重试异常）</li>
 *   <li>任务执行超时被强制中断</li>
 *   <li>补偿扫描发现大量超时任务（可选）</li>
 * </ul>
 *
 * <p>实现注意事项:
 * <ul>
 *   <li>告警发送应异步执行，不应阻塞任务执行主流程</li>
 *   <li>告警发送失败不应影响任务状态流转</li>
 *   <li>建议实现告警去重/防抖，避免同一任务重复告警</li>
 * </ul>
 */
public interface AlarmNotifier {

    /**
     * 获取告警器名称
     *
     * <p>用于日志记录和多个告警器共存时的标识。
     *
     * @return 告警器名称
     */
    String getName();

    /**
     * 发送告警通知
     *
     * <p>当任务进入 DEAD 状态时调用。
     *
     * @param task  进入死信状态的任务实例
     * @param reason 进入 DEAD 的原因（如 "max retry exceeded", "non-retryable exception"）
     */
    void notify(TaskInstance task, String reason);

    /**
     * 发送非任务级告警通知。
     *
     * <p>用于队列积压、窗口失败率等没有单个任务作为告警主体的场景。
     * 默认实现为空，保持已有业务实现兼容。
     *
     * @param alarmType 告警类型
     * @param reason    告警原因
     */
    default void notify(String alarmType, String reason) {
        // no-op
    }

    /**
     * 批量发送告警通知
     *
     * <p>当补偿扫描发现大量超时任务时，可批量告警避免告警风暴。
     *
     * <p>默认实现为逐条调用 {@link #notify(TaskInstance, String)}，
     * 业务方可覆盖此方法实现批量聚合告警。
     *
     * @param tasks  需要告警的任务列表
     * @param reason 告警原因
     */
    default void notifyBatch(java.util.List<TaskInstance> tasks, String reason) {
        for (TaskInstance task : tasks) {
            notify(task, reason);
        }
    }
}
