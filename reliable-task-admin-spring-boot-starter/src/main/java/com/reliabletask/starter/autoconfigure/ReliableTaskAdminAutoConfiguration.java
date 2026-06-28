package com.reliabletask.starter.autoconfigure;

import com.reliabletask.admin.controller.AdminQueryGuard;
import com.reliabletask.admin.controller.TaskAdminController;
import com.reliabletask.core.event.TaskEventPublisher;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskOperationsStore;
import com.reliabletask.core.spi.TaskQueryStore;
import com.reliabletask.core.spi.noop.NoopTaskAuthorizationProvider;
import com.reliabletask.starter.config.ReliableTaskProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 管理后台自动配置类
 *
 * <p>注册管理接口 Controller，使业务应用显式引入 Admin starter 后即可启用 Admin API，
 * 不依赖业务应用扫描 com.reliabletask.admin 包。
 *
 * <p>Admin 模块只在 reliable-task.admin.enabled=true 且存在查询/运维存储能力时装配。
 * 写操作是否可用还要继续受 write-enabled、auth、audit、batch 和确认头配置约束。
 */
@AutoConfiguration(after = {ReliableTaskAutoConfiguration.class, ReliableTaskStoreAutoConfiguration.class})
@ConditionalOnProperty(prefix = "reliable-task.admin", name = "enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnClass(TaskAdminController.class)
@ConditionalOnBean({TaskQueryStore.class, TaskOperationsStore.class})
public class ReliableTaskAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskAuthorizationProvider.class)
    @ConditionalOnProperty(prefix = "reliable-task.admin.auth", name = "enabled", havingValue = "false", matchIfMissing = false)
    public TaskAuthorizationProvider taskAuthorizationProvider() {
        // 只有显式关闭 auth 时才提供 Noop provider；auth 开启但未提供 provider 时 Controller 会拒绝写操作。
        return new NoopTaskAuthorizationProvider();
    }

    @Bean
    @ConditionalOnMissingBean(TaskAdminController.class)
    public TaskAdminController taskAdminController(TaskQueryStore taskQueryStore,
                                                   TaskOperationsStore taskOperationsStore,
                                                   ReliableTaskProperties properties,
                                                   ObjectProvider<TaskAuthorizationProvider> authorizationProvider,
                                                   ObjectProvider<TaskEventPublisher> eventPublisher) {
        ReliableTaskProperties.Admin.Query query = properties.getAdmin().getQuery();
        ReliableTaskProperties.Admin.Console console = properties.getAdmin().getConsole();
        // ObjectProvider 允许业务方按需接入授权和事件发布；未接入时 Controller 内部仍会保持安全降级。
        return new TaskAdminController(taskQueryStore,
                taskOperationsStore,
                properties.getAdmin().getAuth().isEnabled(),
                authorizationProvider.getIfAvailable(),
                properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds(),
                properties.getAdmin().getAudit().isEnabled(),
                properties.getAdmin().getBatch().isEnabled(),
                properties.getAdmin().getMaxPageSize(),
                properties.getAdmin().getMaxBatchLimit(),
                properties.getAdmin().isWriteEnabled(),
                eventPublisher.getIfAvailable(TaskEventPublisher::new),
                new AdminQueryGuard(query.getDefaultWindowHours(),
                        query.getMaxWindowDays(),
                        query.getDefaultLimit(),
                        query.getMaxLimit(),
                        query.getSlowThresholdMs()),
                console.isPayloadPlaintextEnabled(),
                console.getPayloadPreviewLength(),
                console.isWriteConfirmationRequired());
    }
}
