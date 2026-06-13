package com.reliabletask.admin.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Admin 运维查询参数保护器。
 *
 * <p>仅用于 v0.5 新增的运维查询接口，不改变现有任务列表和审计列表的兼容行为。
 */
public class AdminQueryGuard {

    public static final int DEFAULT_WINDOW_HOURS = 24;
    public static final int DEFAULT_MAX_WINDOW_DAYS = 30;
    public static final int DEFAULT_LIMIT = 50;
    public static final int HARD_MAX_LIMIT = 200;
    public static final long DEFAULT_SLOW_THRESHOLD_MS = 30_000L;

    private final int defaultWindowHours;
    private final int maxWindowDays;
    private final int defaultLimit;
    private final int maxLimit;
    private final long slowThresholdMs;

    public AdminQueryGuard(int defaultWindowHours,
                           int maxWindowDays,
                           int defaultLimit,
                           int maxLimit,
                           long slowThresholdMs) {
        this.defaultWindowHours = positiveOrDefault(defaultWindowHours, DEFAULT_WINDOW_HOURS);
        this.maxWindowDays = positiveOrDefault(maxWindowDays, DEFAULT_MAX_WINDOW_DAYS);
        int configuredMaxLimit = positiveOrDefault(maxLimit, HARD_MAX_LIMIT);
        this.maxLimit = Math.min(configuredMaxLimit, HARD_MAX_LIMIT);
        this.defaultLimit = Math.min(positiveOrDefault(defaultLimit, DEFAULT_LIMIT), this.maxLimit);
        this.slowThresholdMs = positiveOrDefault(slowThresholdMs, DEFAULT_SLOW_THRESHOLD_MS);
    }

    public static AdminQueryGuard defaults() {
        return new AdminQueryGuard(DEFAULT_WINDOW_HOURS, DEFAULT_MAX_WINDOW_DAYS,
                DEFAULT_LIMIT, HARD_MAX_LIMIT, DEFAULT_SLOW_THRESHOLD_MS);
    }

    public NormalizedQuery normalize(LocalDateTime createTimeStart,
                                     LocalDateTime createTimeEnd,
                                     Integer limit,
                                     LocalDateTime now) {
        LocalDateTime effectiveNow = Objects.requireNonNullElseGet(now, LocalDateTime::now);
        LocalDateTime effectiveEnd = createTimeEnd != null ? createTimeEnd : effectiveNow;
        LocalDateTime effectiveStart = createTimeStart != null
                ? createTimeStart
                : effectiveEnd.minusHours(defaultWindowHours);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("createTimeStart must be before or equal to createTimeEnd");
        }
        if (Duration.between(effectiveStart, effectiveEnd).compareTo(Duration.ofDays(maxWindowDays)) > 0) {
            throw new IllegalArgumentException("query time window exceeds max-window-days: " + maxWindowDays);
        }

        return new NormalizedQuery(effectiveStart, effectiveEnd, normalizeLimit(limit));
    }

    public int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    public long normalizeSlowThresholdMs(Long durationMsGte) {
        if (durationMsGte == null || durationMsGte <= 0) {
            return slowThresholdMs;
        }
        return durationMsGte;
    }

    public int getDefaultWindowHours() {
        return defaultWindowHours;
    }

    public int getMaxWindowDays() {
        return maxWindowDays;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    public record NormalizedQuery(LocalDateTime createTimeStart,
                                  LocalDateTime createTimeEnd,
                                  int limit) {
    }
}
