package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.AlarmNotifier;

/**
 * 默认告警实现：忽略所有告警事件。
 */
public class NoopAlarmNotifier implements AlarmNotifier {

    @Override
    public String getName() {
        return "noop";
    }

    @Override
    public void notify(TaskInstance task, String reason) {
        // no-op
    }

    @Override
    public void notify(String alarmType, String reason) {
        // no-op
    }
}
