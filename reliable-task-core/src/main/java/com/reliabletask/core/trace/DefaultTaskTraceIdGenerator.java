package com.reliabletask.core.trace;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.spi.TaskTraceIdGenerator;

import java.util.UUID;

/**
 * 默认任务 traceId 生成器。
 *
 * <p>优先复用当前线程 MDC 中的 traceId，使“请求内提交任务”的日志和后续异步执行可以串联。
 * 没有入口 traceId 时生成 rt- 前缀的新 ID，避免提交链路为空。
 */
public class DefaultTaskTraceIdGenerator implements TaskTraceIdGenerator {

    public static final String PREFIX = "rt-";

    @Override
    public String generate(TaskSubmitRequest request) {
        String currentTraceId = TraceContext.getTraceId();
        if (currentTraceId != null && !currentTraceId.isBlank()) {
            return currentTraceId;
        }
        return PREFIX + UUID.randomUUID();
    }
}
