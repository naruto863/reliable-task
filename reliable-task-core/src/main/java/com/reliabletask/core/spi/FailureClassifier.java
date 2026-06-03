package com.reliabletask.core.spi;

import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskInstance;

/**
 * 任务失败分类 SPI。
 *
 * <p>用于在 RetryEngine 中决定失败应进入重试还是直接进入 DEAD。
 */
public interface FailureClassifier {

    /**
     * 对任务失败进行分类。
     *
     * @param task  失败任务
     * @param error 解包后的根异常
     * @return 失败决策
     */
    FailureDecision classify(TaskInstance task, Throwable error);
}
