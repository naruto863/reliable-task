package com.reliabletask.core.spi;

import com.reliabletask.core.model.DeadLetterContext;

/**
 * 任务死信处理 SPI。
 *
 * <p>任务已成功进入 DEAD 后，业务方可通过该 SPI 接入通知、归档或补偿。
 */
@FunctionalInterface
public interface TaskDeadLetterHandler {

    /**
     * 处理器名称，用于日志和排障。
     *
     * @return 处理器名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 处理死信任务。
     *
     * @param context 死信上下文
     */
    void handle(DeadLetterContext context);
}
