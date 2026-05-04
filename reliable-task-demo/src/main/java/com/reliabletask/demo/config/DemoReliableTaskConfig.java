package com.reliabletask.demo.config;

import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.spi.AlarmNotifier;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Demo V2 SPI 接入示例。
 */
@Slf4j
@Configuration
public class DemoReliableTaskConfig {

    private static final Set<String> WRITE_OPERATORS = Set.of("admin", "ops");

    @Bean
    public TaskAuthorizationProvider demoTaskAuthorizationProvider() {
        return (operator, action, taskId) -> {
            if (action == null || action.endsWith("_VIEW") || action.equals("TASK_VIEW")) {
                return operator != null && !operator.isBlank();
            }
            return WRITE_OPERATORS.contains(operator);
        };
    }

    @Bean
    public AlarmNotifier demoAlarmNotifier() {
        return new AlarmNotifier() {
            @Override
            public String getName() {
                return "demo-log";
            }

            @Override
            public void notify(TaskInstance task, String reason) {
                log.warn("ReliableTask demo alarm: taskId={}, taskType={}, bizId={}, reason={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getTaskType(),
                        task == null ? null : task.getBizId(),
                        reason);
            }

            @Override
            public void notify(String alarmType, String reason) {
                log.warn("ReliableTask demo system alarm: type={}, reason={}", alarmType, reason);
            }
        };
    }
}
