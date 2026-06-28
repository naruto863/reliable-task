package com.reliabletask.core.diagnostics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 默认异常诊断格式化器。
 *
 * <p>用于把 Handler 异常转成可入库的错误码、摘要和堆栈片段。
 * 这里做长度截断是为了保护任务表/日志表字段和 Admin 展示，不改变原始异常对象。
 */
public class DefaultTaskExceptionFormatter implements TaskExceptionFormatter {

    private static final int DEFAULT_MESSAGE_LIMIT = 2000;
    private static final int DEFAULT_STACK_LIMIT = 4000;

    private final int messageLimit;
    private final int stackLimit;

    public DefaultTaskExceptionFormatter() {
        this(DEFAULT_MESSAGE_LIMIT, DEFAULT_STACK_LIMIT);
    }

    public DefaultTaskExceptionFormatter(int messageLimit, int stackLimit) {
        this.messageLimit = Math.max(messageLimit, 1);
        this.stackLimit = Math.max(stackLimit, 1);
    }

    @Override
    public TaskFailureDiagnostic format(Throwable error) {
        Throwable rootCause = unwrap(error);
        String errorCode = rootCause.getClass().getSimpleName();
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = errorCode;
        }
        return new TaskFailureDiagnostic(
                truncate(errorCode, 128),
                truncate(message, messageLimit),
                truncate(toStackTrace(rootCause), stackLimit)
        );
    }

    private Throwable unwrap(Throwable error) {
        if (error == null) {
            return new NullPointerException("error");
        }
        Throwable cause = error;
        while (cause.getCause() != null && cause != cause.getCause()) {
            // 异步执行常见 CompletionException/ExecutionException 包装，诊断时展示真正业务根因。
            if (cause instanceof CompletionException || cause instanceof ExecutionException) {
                cause = cause.getCause();
            } else {
                break;
            }
        }
        return cause;
    }

    private String toStackTrace(Throwable error) {
        StringWriter stringWriter = new StringWriter();
        error.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
