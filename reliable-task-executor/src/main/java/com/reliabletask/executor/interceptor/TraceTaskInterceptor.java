package com.reliabletask.executor.interceptor;

import com.reliabletask.core.context.TraceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 trace/MDC 拦截器。
 *
 * <p>该拦截器排在最前进入、最后清理，确保后续 Handler、指标和日志都能读到同一个 traceId。
 * Worker 使用线程池复用线程，任何分支结束后都必须清理 MDC，避免下一次任务继承旧 traceId。
 */
@Slf4j
public class TraceTaskInterceptor implements TaskInterceptor {

    public static final int ORDER = Integer.MIN_VALUE;

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void beforeExecute(TaskExecutionContext context) {
        String traceId = context == null ? null : context.getTraceId();
        if (traceId != null) {
            TraceContext.setTraceId(traceId);
            log.debug("TraceId set for task execution: traceId={}, taskId={}",
                    traceId, context.getTaskId());
        } else {
            // 当前任务没有 traceId 时也主动清理，防止复用线程残留上一笔任务的 MDC。
            TraceContext.clear();
        }
    }

    @Override
    public void afterExecute(TaskExecutionContext context) {
        clearTrace();
    }

    @Override
    public void onError(TaskExecutionContext context, Throwable error) {
        clearTrace();
    }

    private void clearTrace() {
        TraceContext.clear();
        log.debug("TraceId cleared after task execution");
    }
}
