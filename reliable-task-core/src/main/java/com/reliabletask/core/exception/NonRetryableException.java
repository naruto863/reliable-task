package com.reliabletask.core.exception;

/**
 * 不可重试异常标记
 *
 * <p>业务方在任务处理器中抛出此异常时，组件不会进行重试，
 * 直接将任务状态流转为 DEAD（需人工干预）。
 *
 * <p>使用场景:
 * <ul>
 *   <li>业务参数错误（如订单号不存在、金额不合法）</li>
 *   <li>业务规则不满足（如库存不足、账户已注销）</li>
 *   <li>预期重试不会成功的场景（如 404、400 等客户端错误）</li>
 * </ul>
 *
 * <p>示例:
 * <pre>
 * throw new NonRetryableException("订单不存在: " + orderId);
 * </pre>
 */
public class NonRetryableException extends TaskExecutionException {

    public NonRetryableException(String message) {
        super(message);
    }

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableException(Throwable cause) {
        super(cause);
    }
}
