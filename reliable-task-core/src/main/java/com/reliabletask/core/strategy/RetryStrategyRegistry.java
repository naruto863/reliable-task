package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 重试策略注册表。
 *
 * <p>默认注册 FIXED 和 EXPONENTIAL。用户传入的策略会覆盖相同类型或名称的内置策略；
 * 当注册了 type=CUSTOM 的策略后，RetryEngine 可通过 RetryStrategyType.CUSTOM 使用它。
 *
 * <p>注册表同时按枚举类型和名称索引策略。名称索引统一转大写，便于配置项大小写不敏感；
 * 类型索引用于框架内部快速路由，名称索引用于 Spring Boot 配置和用户自定义策略接入。
 */
public class RetryStrategyRegistry {

    private final Map<RetryStrategyType, RetryStrategy> strategiesByType =
            new EnumMap<>(RetryStrategyType.class);
    private final Map<String, RetryStrategy> strategiesByName = new LinkedHashMap<>();

    public RetryStrategyRegistry() {
        this(List.of());
    }

    public RetryStrategyRegistry(Collection<RetryStrategy> customStrategies) {
        this(new FixedRetryStrategy(), new ExponentialRetryStrategy(), customStrategies);
    }

    public RetryStrategyRegistry(RetryStrategy fixedStrategy,
                                 RetryStrategy exponentialStrategy,
                                 Collection<RetryStrategy> customStrategies) {
        register(fixedStrategy);
        register(exponentialStrategy);
        if (customStrategies != null) {
            customStrategies.forEach(this::register);
        }
    }

    public RetryStrategy getStrategy(RetryStrategyType type) {
        // type 为空时回退 EXPONENTIAL，保持历史默认重试语义，不把空配置解释为“不重试”。
        RetryStrategyType effectiveType = type != null ? type : RetryStrategyType.EXPONENTIAL;
        RetryStrategy strategy = strategiesByType.get(effectiveType);
        if (strategy == null) {
            throw new IllegalArgumentException("No RetryStrategy registered for type: " + effectiveType);
        }
        return strategy;
    }

    public RetryStrategy getStrategy(String name) {
        if (name == null || name.isBlank()) {
            return getStrategy((RetryStrategyType) null);
        }
        RetryStrategy strategy = strategiesByName.get(normalizeName(name));
        if (strategy == null) {
            throw new IllegalArgumentException("No RetryStrategy registered for name: " + name);
        }
        return strategy;
    }

    private void register(RetryStrategy strategy) {
        if (strategy == null) {
            return;
        }
        // 后注册覆盖先注册：业务自定义策略可以有意替换内置策略的实现或显示名称。
        RetryStrategyType type = strategy.getType();
        if (type != null) {
            strategiesByType.put(type, strategy);
            strategiesByName.put(normalizeName(type.name()), strategy);
        }
        String name = strategy.getName();
        if (name != null && !name.isBlank()) {
            strategiesByName.put(normalizeName(name), strategy);
        }
    }

    private String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
