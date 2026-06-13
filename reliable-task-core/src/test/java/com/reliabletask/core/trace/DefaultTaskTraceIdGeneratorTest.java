package com.reliabletask.core.trace;

import com.reliabletask.core.context.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTaskTraceIdGeneratorTest {

    private final DefaultTaskTraceIdGenerator generator = new DefaultTaskTraceIdGenerator();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("generate - 优先复用当前 TraceContext")
    void generate_reusesCurrentTraceContext() {
        TraceContext.setTraceId("trace-current");

        String traceId = generator.generate(null);

        assertEquals("trace-current", traceId);
    }

    @Test
    @DisplayName("generate - 无当前 traceId 时生成 rt 前缀 ID")
    void generate_withoutCurrentTraceId_returnsRtPrefixedId() {
        String traceId = generator.generate(null);

        assertTrue(traceId.startsWith(DefaultTaskTraceIdGenerator.PREFIX));
        assertTrue(traceId.length() <= 64);
    }
}
