package com.reliabletask.core.exception;

/**
 * 可重试异常标记
 *
 * <p>业务方在任务处理器中抛出此异常时，组件会按照配置的重试策略进行自动重试。
 *
 * <p>使用场景:
 * <ul>
 *   <li>第三方接口临时不可用（网络抖动、限流、超时）</li>
 *   <li>依赖的外部资源暂时不可用（数据库连接池满、缓存未命中）</li>
 *   <li>预期稍后重试可能成功的业务场景</li>
 * </ul>
 *
 * <p>示例:
 * <pre>
 * throw new RetryableException("外部系统超时", timeoutException);
 * </pre>
 */
public class RetryableException extends TaskExecutionException {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableException(Throwable cause) {
        super(cause);
    }
}
