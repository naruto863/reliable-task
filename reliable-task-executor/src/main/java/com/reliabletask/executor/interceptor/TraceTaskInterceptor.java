package com.reliabletask.executor.interceptor;

import com.reliabletask.core.context.TraceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 trace/MDC 拦截器。
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
