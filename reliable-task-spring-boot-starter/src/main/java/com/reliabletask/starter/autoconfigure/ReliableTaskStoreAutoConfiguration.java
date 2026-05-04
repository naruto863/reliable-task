package com.reliabletask.starter.autoconfigure;

import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.store.config.MyBatisPlusMetaObjectHandler;
import com.reliabletask.store.impl.MyBatisTaskStore;
import com.reliabletask.store.mapper.ReliableTaskAuditLogMapper;
import com.reliabletask.store.mapper.ReliableTaskBatchOperationMapper;
import com.reliabletask.store.mapper.ReliableTaskLogMapper;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import com.reliabletask.store.mapper.ReliableTaskWorkerMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 存储层自动配置类
 *
 * <p>注册 MyBatisTaskStore 和 MyBatis-Plus 相关 Bean。
 * 通过 reliable-task.enabled 和 reliable-task.store 控制开关。
 */
@AutoConfiguration(after = ReliableTaskAutoConfiguration.class)
@ConditionalOnProperty(prefix = "reliable-task", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({MyBatisTaskStore.class, ReliableTaskMapper.class, ReliableTaskLogMapper.class})
@MapperScan(basePackageClasses = ReliableTaskMapper.class)
public class ReliableTaskStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskStore.class)
    public TaskStore taskStore(ReliableTaskMapper taskMapper,
                               ReliableTaskLogMapper taskLogMapper,
                               ReliableTaskWorkerMapper workerMapper,
                               ReliableTaskAuditLogMapper auditLogMapper,
                               ReliableTaskBatchOperationMapper batchOperationMapper) {
        return new MyBatisTaskStore(taskMapper, taskLogMapper,
                workerMapper, auditLogMapper, batchOperationMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MyBatisPlusMetaObjectHandler.class)
    public MyBatisPlusMetaObjectHandler myBatisPlusMetaObjectHandler() {
        return new MyBatisPlusMetaObjectHandler();
    }
}
