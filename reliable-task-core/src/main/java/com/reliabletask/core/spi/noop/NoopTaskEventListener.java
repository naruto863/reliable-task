package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.spi.TaskEventListener;

/**
 * 默认空任务事件监听器。
 */
public class NoopTaskEventListener implements TaskEventListener {

    @Override
    public void onEvent(TaskEvent event) {
        // no-op
    }
}
