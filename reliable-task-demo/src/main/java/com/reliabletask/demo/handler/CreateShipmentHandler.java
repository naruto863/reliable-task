package com.reliabletask.demo.handler;

import com.reliabletask.core.annotation.TaskHandler;
import com.reliabletask.core.annotation.TaskRetryable;
import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.demo.model.ShipmentPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 创建发货单处理器示例
 *
 * <p>演示:
 * 1. 使用 @TaskHandler 注解自动注册
 * 2. 使用 @TaskRetryable 配置重试策略
 * 3. 模拟第三方调用失败后自动重试成功
 */
@Slf4j
@Component
@TaskHandler("CREATE_SHIPMENT")
@TaskRetryable(maxRetryCount = 3, retryIntervalMs = 2000, maxDelayMs = 30000)
public class CreateShipmentHandler implements com.reliabletask.core.spi.TaskHandler {

    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Override
    public String getTaskType() {
        return "CREATE_SHIPMENT";
    }

    @Override
    public Class<?> payloadType() {
        return ShipmentPayload.class;
    }

    @Override
    public void execute(TaskInstance task) {
        throw new UnsupportedOperationException("CREATE_SHIPMENT requires ShipmentPayload");
    }

    @Override
    public void execute(TaskInstance task, Object payload) throws Exception {
        ShipmentPayload shipmentPayload = (ShipmentPayload) payload;
        log.info("Processing shipment creation: orderNo={}, buyerId={}, address={}",
                shipmentPayload.getOrderNo(), shipmentPayload.getBuyerId(), shipmentPayload.getAddress());

        if (shipmentPayload.isForceNonRetryable()
                || (task.getBizId() != null && task.getBizId().startsWith("NON_RETRYABLE-"))) {
            throw new NonRetryableException("Invalid order data for demo: " + task.getBizId());
        }

        // 模拟第三方物流 API 调用
        int attempt = attempts.computeIfAbsent(task.getBizId(), key -> new AtomicInteger()).incrementAndGet();
        if (attempt <= 2) {
            log.warn("Shipment API call failed (attempt {}): orderNo={}", attempt, task.getBizId());
            throw new RuntimeException("Shipment API timeout (simulated failure #" + attempt + ")");
        }

        // 模拟成功
        log.info("Shipment created successfully: orderNo={}, after {} attempts", task.getBizId(), attempt);
    }
}
