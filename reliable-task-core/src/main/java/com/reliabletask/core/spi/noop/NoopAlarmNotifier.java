package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.AlarmNotifier;

/**
 * 默认告警实现：忽略所有告警事件。
 *
 * <p>用于未配置告警通道或 alert.enabled=false 的场景。它不会抛异常，也不会记录外部副作用；
 * 因此生产环境需要真实告警时必须显式注册 AlarmNotifier。
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
