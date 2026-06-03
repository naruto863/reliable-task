package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.spi.TaskEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 将任务状态事件转成低基数 Micrometer 计数器。
 */
public class MicrometerTaskEventListener implements TaskEventListener {

    private final MeterRegistry meterRegistry;

    public MicrometerTaskEventListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onEvent(TaskEvent event) {
        if (event == null || event.getEventType() != TaskEventType.RECOVERED) {
            return;
        }
        Counter.builder("reliable_task_recovered_total")
                .tag("task_type", valueOrUnknown(event.getTaskType()))
                .register(meterRegistry)
                .increment();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
