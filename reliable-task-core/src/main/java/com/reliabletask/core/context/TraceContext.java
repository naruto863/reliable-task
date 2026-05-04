package com.reliabletask.core.context;

import org.slf4j.MDC;

/**
 * 链路追踪上下文工具类
 *
 * <p>基于 Slf4j MDC 实现 traceId 的存储和获取。
 * MDC 是 ThreadLocal 的，天然支持线程隔离。
 *
 * <p>使用方式:
 * <pre>
 * // 设置 traceId（通常在请求入口处由网关或过滤器设置）
 * TraceContext.setTraceId("trace-abc123");
 *
 * // 获取 traceId
 * String traceId = TraceContext.getTraceId();
 *
 * // 清理（请求结束时）
 * TraceContext.clear();
 * </pre>
 *
 * <p>日志配置: 在 logback/log4j2 配置中使用 %X{traceId} 即可在日志中输出 traceId。
 */
public class TraceContext {

    /**
     * MDC 中 traceId 的 key
     */
    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    /**
     * 获取当前线程的 traceId
     *
     * @return traceId，如果未设置则返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置当前线程的 traceId
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清理当前线程的 traceId
     *
     * <p>必须在请求/任务结束时调用，防止线程池复用导致 traceId 泄漏。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
