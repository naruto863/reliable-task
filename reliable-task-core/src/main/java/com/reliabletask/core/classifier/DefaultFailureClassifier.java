package com.reliabletask.core.classifier;

import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.FailureClassifier;

/**
 * 默认失败分类器。
 *
 * <p>保持历史行为：NonRetryableException 直接 DEAD，其他异常进入重试判断。
 */
public class DefaultFailureClassifier implements FailureClassifier {

    @Override
    public FailureDecision classify(TaskInstance task, Throwable error) {
        if (error instanceof NonRetryableException) {
            return FailureDecision.dead("non-retryable exception");
        }
        return FailureDecision.retry("retryable exception");
    }
}
