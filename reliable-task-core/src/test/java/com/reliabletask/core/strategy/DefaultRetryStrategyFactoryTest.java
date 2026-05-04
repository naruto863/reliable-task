package com.reliabletask.core.strategy;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.spi.RetryStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultRetryStrategyFactory 测试")
class DefaultRetryStrategyFactoryTest {

    @Test
    @DisplayName("getStrategy(FIXED) 返回 FixedRetryStrategy")
    void getStrategy_fixed_returnsFixedStrategy() {
        RetryStrategy strategy = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.FIXED);
        assertInstanceOf(FixedRetryStrategy.class, strategy);
        assertEquals(RetryStrategyType.FIXED, strategy.getType());
    }

    @Test
    @DisplayName("getStrategy(EXPONENTIAL) 返回 ExponentialRetryStrategy")
    void getStrategy_exponential_returnsExponentialStrategy() {
        RetryStrategy strategy = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.EXPONENTIAL);
        assertInstanceOf(ExponentialRetryStrategy.class, strategy);
        assertEquals(RetryStrategyType.EXPONENTIAL, strategy.getType());
    }

    @Test
    @DisplayName("getStrategy(null) 返回默认指数策略")
    void getStrategy_null_returnsDefaultExponentialStrategy() {
        RetryStrategy strategy = DefaultRetryStrategyFactory.getStrategy(null);
        assertInstanceOf(ExponentialRetryStrategy.class, strategy);
    }

    @Test
    @DisplayName("getStrategy(CUSTOM) 抛异常")
    void getStrategy_custom_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.CUSTOM));
    }

    @Test
    @DisplayName("exponential() 快捷方法返回指数策略")
    void exponential_returnsExponentialStrategy() {
        RetryStrategy strategy = DefaultRetryStrategyFactory.exponential();
        assertInstanceOf(ExponentialRetryStrategy.class, strategy);
    }

    @Test
    @DisplayName("fixed() 快捷方法返回固定策略")
    void fixed_returnsFixedStrategy() {
        RetryStrategy strategy = DefaultRetryStrategyFactory.fixed();
        assertInstanceOf(FixedRetryStrategy.class, strategy);
    }

    @Test
    @DisplayName("策略对象复用 - 同一类型返回相同实例")
    void getStrategy_returnsSameInstance() {
        RetryStrategy fixed1 = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.FIXED);
        RetryStrategy fixed2 = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.FIXED);
        assertSame(fixed1, fixed2);

        RetryStrategy exp1 = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.EXPONENTIAL);
        RetryStrategy exp2 = DefaultRetryStrategyFactory.getStrategy(RetryStrategyType.EXPONENTIAL);
        assertSame(exp1, exp2);
    }
}
