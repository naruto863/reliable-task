package com.reliabletask.starter.metrics;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.model.TaskEvent;
import com.reliabletask.core.spi.TaskEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 将任务状态事件转成低基数 Micrometer 计数器。
 *
 * <p>当前只监听 RECOVERED 事件，补足执行指标无法表达的“超时恢复”维度。
 * 标签只保留 task_type，避免把 taskId、bizId 等高基数字段打进指标系统。
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
        // 恢复事件来自 TaskRecoveryScheduler，而不是 Handler 执行结果，因此单独建计数器。
        Counter.builder("reliable_task_recovered_total")
                .tag("task_type", valueOrUnknown(event.getTaskType()))
                .register(meterRegistry)
                .increment();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
