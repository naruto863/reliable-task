package com.reliabletask.starter.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.reliabletask.core.spi.TaskStore;
import com.reliabletask.store.config.MyBatisPlusMetaObjectHandler;
import com.reliabletask.store.impl.MyBatisTaskStore;
import com.reliabletask.store.mapper.ReliableTaskAuditLogMapper;
import com.reliabletask.store.mapper.ReliableTaskBatchOperationMapper;
import com.reliabletask.store.mapper.ReliableTaskLogMapper;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import com.reliabletask.store.mapper.ReliableTaskWorkerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReliableTaskStoreAutoConfiguration 测试")
class ReliableTaskStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class,
                    ReliableTaskAutoConfiguration.class,
                    ReliableTaskStoreAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:reliable_task_store_auto_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=");

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
}
