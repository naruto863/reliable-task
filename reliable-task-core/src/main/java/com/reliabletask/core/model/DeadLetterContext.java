package com.reliabletask.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务进入 DEAD 后传递给死信处理器的上下文。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterContext {

    /**
     * 已进入 DEAD 的任务。
     */
    private TaskInstance task;

    /**
     * 错误码或异常类型。
     */
    private String errorCode;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 进入 DEAD 的业务或系统原因。
     */
    private String reason;

    /**
     * 是否因为重试耗尽进入 DEAD。
     */
    private boolean retriesExhausted;

    /**
     * 触发死信处理的来源，例如 TaskExecutor 或 RetryEngine。
     */
    private String source;

    /**
     * 任务进入 DEAD 的时间。
     */
    private LocalDateTime deadAt;
}
