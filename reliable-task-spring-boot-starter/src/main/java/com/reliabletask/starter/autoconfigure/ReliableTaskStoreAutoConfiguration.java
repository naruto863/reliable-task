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
 *
 * <p>如果业务方提供了自己的 TaskStore Bean，本配置不会再创建 MyBatisTaskStore。
 * 这样可以在不引入默认表结构的情况下接入自研存储，但自定义实现需要同时关注命令、查询和运维能力边界。
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
        // 默认实现一次性注入 V2 运维相关 Mapper；低版本兼容构造器仍保留在 MyBatisTaskStore 内部。
        return new MyBatisTaskStore(taskMapper, taskLogMapper,
                workerMapper, auditLogMapper, batchOperationMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MyBatisPlusMetaObjectHandler.class)
    public MyBatisPlusMetaObjectHandler myBatisPlusMetaObjectHandler() {
        // 统一补 createTime/updateTime，避免各个写入入口重复维护时间字段。
        return new MyBatisPlusMetaObjectHandler();
    }
}
