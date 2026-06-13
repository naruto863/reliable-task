package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskSubmitRequest;

/**
 * 任务投递 traceId 生成 SPI。
 */
public interface TaskTraceIdGenerator {

    /**
     * 为本次投递生成或复用 traceId。
     *
     * @param request 投递请求
     * @return traceId，长度必须满足 schema 中 VARCHAR(64) 约束
     */
    String generate(TaskSubmitRequest request);
}
