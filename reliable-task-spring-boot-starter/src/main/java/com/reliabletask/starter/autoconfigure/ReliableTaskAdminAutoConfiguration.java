package com.reliabletask.starter.autoconfigure;

import com.reliabletask.admin.controller.TaskAdminController;
import com.reliabletask.core.spi.TaskAuthorizationProvider;
import com.reliabletask.core.spi.TaskStore;
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
 * <p>注册管理接口 Controller，使业务应用只引入 starter 即可启用 Admin API，
 * 不依赖业务应用扫描 com.reliabletask.admin 包。
 */
@AutoConfiguration(after = {ReliableTaskAutoConfiguration.class, ReliableTaskStoreAutoConfiguration.class})
@ConditionalOnProperty(prefix = "reliable-task.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(TaskAdminController.class)
@ConditionalOnBean(TaskStore.class)
public class ReliableTaskAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskAuthorizationProvider.class)
    @ConditionalOnProperty(prefix = "reliable-task.admin.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    public TaskAuthorizationProvider taskAuthorizationProvider() {
        return new NoopTaskAuthorizationProvider();
    }

    @Bean
    @ConditionalOnMissingBean(TaskAdminController.class)
    public TaskAdminController taskAdminController(TaskStore taskStore,
                                                   ReliableTaskProperties properties,
                                                   ObjectProvider<TaskAuthorizationProvider> authorizationProvider) {
        return new TaskAdminController(taskStore,
                properties.getAdmin().getAuth().isEnabled(),
                authorizationProvider.getIfAvailable(),
                properties.getWorker().getHeartbeat().getStaleWorkerThresholdSeconds(),
                properties.getAdmin().getAudit().isEnabled(),
                properties.getAdmin().getBatch().isEnabled());
    }
}
