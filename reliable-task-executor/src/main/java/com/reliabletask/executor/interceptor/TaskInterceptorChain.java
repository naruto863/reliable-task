package com.reliabletask.executor.interceptor;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * TaskInterceptor 链。
 *
 * <p>before 按 order 升序执行；after/onError 按逆序执行。拦截器自身异常会被记录并隔离，
 * 避免覆盖 Handler 的原始执行结果。
 *
 * <p>反向执行 after/onError 是典型栈式语义：越早进入的拦截器越晚退出，便于 trace、
 * 资源清理、计时等横切逻辑形成成对的 enter/exit 边界。
 */
@Slf4j
public class TaskInterceptorChain {

    private final List<TaskInterceptor> interceptors;

    public TaskInterceptorChain(List<TaskInterceptor> interceptors) {
        this.interceptors = interceptors == null
                ? List.of()
                : interceptors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TaskInterceptor::order))
                .toList();
    }

    public static TaskInterceptorChain of(List<TaskInterceptor> interceptors) {
        return new TaskInterceptorChain(interceptors);
    }

    public List<TaskInterceptor> getInterceptors() {
        return interceptors;
    }

    public void beforeExecute(TaskExecutionContext context) {
        TaskExecutionContext safeContext = safeContext(context);
        for (TaskInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeExecute(safeContext);
            } catch (RuntimeException e) {
                log.warn("TaskInterceptor beforeExecute failed: interceptor={}, taskId={}, reason={}",
                        interceptorName(interceptor), safeContext.getTaskId(), e.getMessage());
            }
        }
    }

    public void afterExecute(TaskExecutionContext context) {
        TaskExecutionContext safeContext = safeContext(context);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            TaskInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.afterExecute(safeContext);
            } catch (RuntimeException e) {
                log.warn("TaskInterceptor afterExecute failed: interceptor={}, taskId={}, reason={}",
                        interceptorName(interceptor), safeContext.getTaskId(), e.getMessage());
            }
        }
    }

    public void onError(TaskExecutionContext context, Throwable error) {
        TaskExecutionContext safeContext = safeContext(context);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            TaskInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.onError(safeContext, error);
            } catch (RuntimeException e) {
                log.warn("TaskInterceptor onError failed: interceptor={}, taskId={}, originalError={}, reason={}",
                        interceptorName(interceptor), safeContext.getTaskId(),
                        error == null ? null : error.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private static TaskExecutionContext safeContext(TaskExecutionContext context) {
        return context != null ? context : TaskExecutionContext.empty();
    }

    private static String interceptorName(TaskInterceptor interceptor) {
        return interceptor.getClass().getName();
    }
}
