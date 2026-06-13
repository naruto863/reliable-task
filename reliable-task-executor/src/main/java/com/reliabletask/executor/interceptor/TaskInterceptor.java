package com.reliabletask.executor.interceptor;

/**
 * 可排序、可组合的任务执行拦截器 SPI。
 */
public interface TaskInterceptor {

    /**
     * 越小越先执行 before，越晚执行 after/onError。
     */
    default int order() {
        return 0;
    }

    /**
     * Handler 执行前回调。
     *
     * @param context 任务执行上下文
     */
    default void beforeExecute(TaskExecutionContext context) {
    }

    /**
     * Handler 正常或异常结束后的清理回调。
     *
     * @param context 任务执行上下文
     */
    default void afterExecute(TaskExecutionContext context) {
    }

    /**
     * Handler 抛出异常后的观察回调。
     *
     * @param context 任务执行上下文
     * @param error   Handler 或执行链路抛出的原始异常
     */
    default void onError(TaskExecutionContext context, Throwable error) {
    }
}
