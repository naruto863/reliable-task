package com.reliabletask.starter.autoconfigure;

import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.store.config.MyBatisPlusMetaObjectHandler;
import com.reliabletask.store.impl.MyBatisTaskStore;
import com.reliabletask.store.mapper.ReliableTaskAuditLogMapper;
import com.reliabletask.store.mapper.ReliableTaskBatchOperationMapper;
import com.reliabletask.store.mapper.ReliableTaskLogMapper;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import com.reliabletask.store.mapper.ReliableTaskWorkerMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReliableTaskStoreAutoConfiguration 测试")
class ReliableTaskStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReliableTaskAutoConfiguration.class,
                    ReliableTaskStoreAutoConfiguration.class))
            .withUserConfiguration(MyBatisTestConfiguration.class);

    @Test
    @DisplayName("自动扫描 store Mapper 并注册 TaskStore")
    void defaultConfiguration_scansStoreMappersAndRegistersTaskStore() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReliableTaskMapper.class);
            assertThat(context).hasSingleBean(ReliableTaskLogMapper.class);
            assertThat(context).hasSingleBean(ReliableTaskWorkerMapper.class);
            assertThat(context).hasSingleBean(ReliableTaskAuditLogMapper.class);
            assertThat(context).hasSingleBean(ReliableTaskBatchOperationMapper.class);
            assertThat(context).hasSingleBean(TaskStore.class);
            assertThat(context).getBean(TaskStore.class)
                    .isInstanceOf(MyBatisTaskStore.class);
            assertThat(context).hasSingleBean(MyBatisPlusMetaObjectHandler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MyBatisTestConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory() {
            return new SqlSessionFactoryBuilder()
                    .build(new org.apache.ibatis.session.Configuration());
        }
    }
}
