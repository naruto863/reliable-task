package com.reliabletask.core.exception;

/**
 * 任务执行异常
 *
 * <p>用于标记任务处理器执行过程中发生的异常。
 * 可通过 cause 包装原始异常，保留完整堆栈信息。
 */
public class TaskExecutionException extends ReliableTaskException {

    public TaskExecutionException(String message) {
        super(message);
    }

    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public TaskExecutionException(Throwable cause) {
        super(cause);
    }
}
