package com.reliabletask.starter.autoconfigure;

import com.reliabletask.starter.config.ReliableTaskProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ReliableTask 主配置类
 *
 * <p>注册 ReliableTaskProperties 配置属性 Bean，
 * 作为其他自动配置类的前置依赖。
 */
@Configuration
@EnableConfigurationProperties(ReliableTaskProperties.class)
public class ReliableTaskAutoConfiguration {
}
