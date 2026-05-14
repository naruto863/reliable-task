package com.reliabletask.core.diagnostics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 默认异常诊断格式化器。
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
