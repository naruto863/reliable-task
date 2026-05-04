package com.reliabletask.core.exception;

/**
 * ReliableTask 组件基础异常
 *
 * <p>所有组件内部异常均继承此类，便于统一捕获和处理。
 */
public class ReliableTaskException extends RuntimeException {

    public ReliableTaskException(String message) {
        super(message);
    }

    public ReliableTaskException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReliableTaskException(Throwable cause) {
        super(cause);
    }
}
