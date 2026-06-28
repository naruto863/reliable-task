package com.reliabletask.core.classifier;

import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.FailureClassifier;

/**
 * 默认失败分类器。
 *
 * <p>保持历史行为：NonRetryableException 直接 DEAD，其他异常进入重试判断。
 * 这是最小且保守的默认策略：框架只识别业务显式声明的不可重试异常，
 * 不根据异常类型名称或错误消息猜测是否应该终止任务。
 */
public class DefaultFailureClassifier implements FailureClassifier {

    @Override
    public FailureDecision classify(TaskInstance task, Throwable error) {
        if (error instanceof NonRetryableException) {
            // 业务方抛出 NonRetryableException 表示继续重试没有意义，例如参数非法或状态已终态。
            return FailureDecision.dead("non-retryable exception");
        }
        return FailureDecision.retry("retryable exception");
    }
}
