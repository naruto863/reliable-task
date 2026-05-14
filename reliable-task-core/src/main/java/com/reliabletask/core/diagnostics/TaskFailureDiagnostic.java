package com.reliabletask.core.diagnostics;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务失败诊断信息。
 */
@Getter
@AllArgsConstructor
public class TaskFailureDiagnostic {

    /**
     * 稳定错误码，默认使用异常简单类名。
     */
    private final String errorCode;

    /**
     * 面向主表和审计的短摘要。
     */
    private final String errorMessage;

    /**
     * 面向执行日志的压缩堆栈。
     */
    private final String stackTrace;
}
