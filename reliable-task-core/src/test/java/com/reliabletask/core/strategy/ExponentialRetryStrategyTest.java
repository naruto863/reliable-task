package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.exception.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExponentialRetryStrategy 测试")
class ExponentialRetryStrategyTest {

    @Test
    @DisplayName("getType 返回 EXPONENTIAL")
    void getType_returnsExponential() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        assertEquals(RetryStrategyType.EXPONENTIAL, strategy.getType());
    }

    @Test
    @DisplayName("nextDelayMs - 默认 multiplier=2.0, 指数增长")
    void nextDelayMs_defaultMultiplier_exponentialGrowth() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        long intervalMs = 1000L;
        long maxDelayMs = 300000L;

        assertEquals(1000L, strategy.nextDelayMs(0, intervalMs, maxDelayMs));
        assertEquals(2000L, strategy.nextDelayMs(1, intervalMs, maxDelayMs));
        assertEquals(4000L, strategy.nextDelayMs(2, intervalMs, maxDelayMs));
        assertEquals(8000L, strategy.nextDelayMs(3, intervalMs, maxDelayMs));
        assertEquals(16000L, strategy.nextDelayMs(4, intervalMs, maxDelayMs));
        assertEquals(32000L, strategy.nextDelayMs(5, intervalMs, maxDelayMs));
    }

    @Test
    @DisplayName("nextDelayMs - 自定义 multiplier=1.5")
    void nextDelayMs_customMultiplier() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy(1.5);
        long intervalMs = 1000L;
        long maxDelayMs = 300000L;

        assertEquals(1000L, strategy.nextDelayMs(0, intervalMs, maxDelayMs));
        assertEquals(1500L, strategy.nextDelayMs(1, intervalMs, maxDelayMs));
        assertEquals(2250L, strategy.nextDelayMs(2, intervalMs, maxDelayMs));
    }

    @Test
    @DisplayName("nextDelayMs - 不超过 maxDelayMs 上限")
    void nextDelayMs_capsAtMaxDelayMs() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        long intervalMs = 1000L;
        long maxDelayMs = 5000L;

        long delay0 = strategy.nextDelayMs(0, intervalMs, maxDelayMs);
        long delay1 = strategy.nextDelayMs(1, intervalMs, maxDelayMs);
        long delay2 = strategy.nextDelayMs(2, intervalMs, maxDelayMs);
        long delay3 = strategy.nextDelayMs(3, intervalMs, maxDelayMs);

        assertEquals(1000L, delay0);
        assertEquals(2000L, delay1);
        assertEquals(4000L, delay2);
        assertEquals(5000L, delay3);
    }

    @Test
    @DisplayName("nextDelayMs - jitterRatio 使用可控随机源计算抖动")
    void nextDelayMs_jitterRatio_usesDeterministicRandomSource() {
        ExponentialRetryStrategy lowJitter = new ExponentialRetryStrategy(2.0, 0.2, () -> 0.0D);
        ExponentialRetryStrategy midJitter = new ExponentialRetryStrategy(2.0, 0.2, () -> 0.5D);
        ExponentialRetryStrategy highJitter = new ExponentialRetryStrategy(2.0, 0.2, () -> 1.0D);

        assertEquals(800L, lowJitter.nextDelayMs(0, 1000L, 5000L));
        assertEquals(1000L, midJitter.nextDelayMs(0, 1000L, 5000L));
        assertEquals(1200L, highJitter.nextDelayMs(0, 1000L, 5000L));
    }

    @Test
    @DisplayName("nextDelayMs - jitter 后仍不超过 maxDelayMs 且不为负数")
    void nextDelayMs_jitteredDelayStaysWithinBounds() {
        ExponentialRetryStrategy highJitter = new ExponentialRetryStrategy(2.0, 0.5, () -> 1.0D);
        ExponentialRetryStrategy lowJitter = new ExponentialRetryStrategy(2.0, 1.0, () -> 0.0D);

        assertEquals(1000L, highJitter.nextDelayMs(0, 1000L, 1000L));
        assertEquals(0L, lowJitter.nextDelayMs(0, 1000L, 5000L));
    }

    @Test
    @DisplayName("nextDelayMs - retryCount=0 返回 intervalMs")
    void nextDelayMs_retryCountZero_returnsBaseInterval() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy(3.0);
        assertEquals(2000L, strategy.nextDelayMs(0, 2000L, 60000L));
    }

    @Test
    @DisplayName("nextDelayMs - multiplier<=1.0 抛异常")
    void nextDelayMs_invalidMultiplier_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialRetryStrategy(1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialRetryStrategy(0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialRetryStrategy(-1.0));
    }

    @Test
    @DisplayName("nextDelayMs - jitterRatio 不在 0 到 1 之间抛异常")
    void nextDelayMs_invalidJitterRatio_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialRetryStrategy(2.0, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialRetryStrategy(2.0, 1.1));
    }

    @Test
    @DisplayName("isRetryable - RetryableException 可重试")
    void isRetryable_retryableException_returnsTrue() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        assertTrue(strategy.isRetryable(new RetryableException("test")));
    }

    @Test
    @DisplayName("isRetryable - NonRetryableException 不可重试")
    void isRetryable_nonRetryableException_returnsFalse() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        assertFalse(strategy.isRetryable(new NonRetryableException("test")));
    }

    @Test
    @DisplayName("isRetryable - 普通异常可重试")
    void isRetryable_normalException_returnsTrue() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        assertTrue(strategy.isRetryable(new RuntimeException("timeout")));
    }
}
