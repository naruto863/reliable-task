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
