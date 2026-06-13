package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.DeadLetterContext;
import com.reliabletask.core.spi.TaskDeadLetterHandler;

/**
 * 默认空死信处理器。
 */
public class NoopTaskDeadLetterHandler implements TaskDeadLetterHandler {

    @Override
    public void handle(DeadLetterContext context) {
        // no-op
    }
}
