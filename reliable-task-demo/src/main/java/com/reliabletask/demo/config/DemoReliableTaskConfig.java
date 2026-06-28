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
 *
 * <p>这里只演示如何接入授权和告警 SPI，权限规则与告警投递都刻意保持简单：
 * 读操作要求有 operator，写操作只允许 admin/ops，告警只写日志。
 * 生产环境应替换为企业自己的账号体系、审计策略和告警渠道。
 */
@Slf4j
@Configuration
public class DemoReliableTaskConfig {

    private static final Set<String> WRITE_OPERATORS = Set.of("admin", "ops");

    @Bean
    public TaskAuthorizationProvider demoTaskAuthorizationProvider() {
        return (operator, action, taskId) -> {
            // Demo 约定 *_VIEW/TASK_VIEW 为只读动作，用于展示 Admin 查询授权的最小接入方式。
            if (action == null || action.endsWith("_VIEW") || action.equals("TASK_VIEW")) {
                return operator != null && !operator.isBlank();
            }
            // 写动作必须显式命中白名单；实际项目通常应接入 RBAC/审批/工单系统。
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
                // Demo 使用日志模拟告警通道，避免示例工程依赖外部 IM、短信或电话平台。
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
