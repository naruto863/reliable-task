package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.exception.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FixedRetryStrategy 测试")
class FixedRetryStrategyTest {

    private FixedRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FixedRetryStrategy();
    }

    @Test
    @DisplayName("getType 返回 FIXED")
    void getType_returnsFixed() {
        assertEquals(RetryStrategyType.FIXED, strategy.getType());
    }

    @Test
    @DisplayName("nextDelayMs - 始终返回 intervalMs")
    void nextDelayMs_alwaysReturnsIntervalMs() {
        long intervalMs = 2000L;
        long maxDelayMs = 60000L;

        assertEquals(2000L, strategy.nextDelayMs(0, intervalMs, maxDelayMs));
        assertEquals(2000L, strategy.nextDelayMs(1, intervalMs, maxDelayMs));
        assertEquals(2000L, strategy.nextDelayMs(5, intervalMs, maxDelayMs));
        assertEquals(2000L, strategy.nextDelayMs(10, intervalMs, maxDelayMs));
    }

    @Test
    @DisplayName("nextDelayMs - intervalMs 超过 maxDelayMs 时返回 maxDelayMs")
    void nextDelayMs_capsAtMaxDelayMs() {
        long intervalMs = 120000L;
        long maxDelayMs = 60000L;

        assertEquals(60000L, strategy.nextDelayMs(0, intervalMs, maxDelayMs));
    }

    @Test
    @DisplayName("nextDelayMs - 不同间隔值")
    void nextDelayMs_differentIntervals() {
        assertEquals(1000L, strategy.nextDelayMs(0, 1000L, 30000L));
        assertEquals(5000L, strategy.nextDelayMs(3, 5000L, 30000L));
        assertEquals(500L, strategy.nextDelayMs(1, 500L, 30000L));
    }

    @Test
    @DisplayName("isRetryable - RetryableException 可重试")
    void isRetryable_retryableException_returnsTrue() {
        assertTrue(strategy.isRetryable(new RetryableException("test")));
    }

    @Test
    @DisplayName("isRetryable - NonRetryableException 不可重试")
    void isRetryable_nonRetryableException_returnsFalse() {
        assertFalse(strategy.isRetryable(new NonRetryableException("test")));
    }

    @Test
    @DisplayName("isRetryable - 普通异常可重试")
    void isRetryable_normalException_returnsTrue() {
        assertTrue(strategy.isRetryable(new RuntimeException("connection timeout")));
    }
}
