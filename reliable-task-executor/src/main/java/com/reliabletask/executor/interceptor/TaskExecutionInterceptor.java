package com.reliabletask.executor.interceptor;

import com.reliabletask.core.model.TaskInstance;

/**
 * 旧任务执行拦截器兼容类。
 *
 * <p>在任务执行前后管理 MDC 中的 traceId:
 * <ul>
 *   <li>执行前: 将 TaskInstance.traceId 设置到 MDC，使 Handler 执行期间的所有日志包含 traceId</li>
 *   <li>执行后: 清理 MDC，防止线程池复用导致 traceId 泄漏</li>
 * </ul>
 *
 * <p>状态流转:
 * <pre>
 *   Worker 拉取任务 → TaskExecutor.execute(task)
 *   → beforeExecute: MDC.put(traceId, task.traceId)
 *   → Handler.execute(task)  ← 此期间日志自动包含 traceId
 *   → afterExecute: MDC.remove(traceId)
 * </pre>
 *
 * <p>v0.6 起推荐使用 {@link TaskInterceptor}、{@link TaskInterceptorChain}
 * 和默认 {@link TraceTaskInterceptor} 组合；本类保留旧构造路径兼容。
 */
public class TaskExecutionInterceptor extends TraceTaskInterceptor {

    /**
     * 执行前拦截
     *
     * <p>将任务携带的 traceId 设置到 MDC。
     * 如果任务没有 traceId（如补偿扫描重新入队的任务），跳过设置。
     *
     * @param task 当前执行的任务实例
     */
    public void beforeExecute(TaskInstance task) {
        beforeExecute(TaskExecutionContext.from(task));
    }

    /**
     * 执行后拦截
     *
     * <p>清理 MDC 中的 traceId，防止线程池复用导致 traceId 泄漏到下一个任务。
     * 无论执行成功还是失败都必须调用。
     */
    public void afterExecute() {
        afterExecute(TaskExecutionContext.empty());
    }
}
