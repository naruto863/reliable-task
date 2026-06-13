package com.reliabletask.core.trace;

import com.reliabletask.core.context.TraceContext;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.spi.TaskTraceIdGenerator;

import java.util.UUID;

/**
 * 默认任务 traceId 生成器。
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
