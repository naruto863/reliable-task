package com.reliabletask.demo.service;

import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.spi.TaskTemplate;
import com.reliabletask.demo.model.ShipmentPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单服务示例
 *
 * <p>演示在事务方法中投递异步任务的完整流程。
 * 订单创建成功后，通过 TaskTemplate 投递发货任务，
 * 任务会在当前业务事务内写入任务表，业务事务回滚时任务写入一并回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final TaskTemplate taskTemplate;

    /**
     * 创建订单并投递发货任务
     *
     * <p>流程:
     * 1. 创建订单（模拟）
     * 2. 投递发货任务到可靠任务队列
     * 3. 当前事务内写入任务表，返回稳定 taskId
     *
     * @param orderNo 订单号
     * @param buyerId 买家ID
     * @return 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(String orderNo, String buyerId) {
        log.info("Creating order: orderNo={}, buyerId={}", orderNo, buyerId);

        // 模拟创建订单
        log.info("Order created successfully: {}", orderNo);

        String taskId = submitShipmentTask(orderNo, buyerId);

        log.info("Shipment task submitted: taskId={}", taskId);
        return taskId;
    }

    /**
     * 创建不可重试订单示例。
     *
     * <p>Handler 会识别 NON_RETRYABLE 前缀并抛出 NonRetryableException，
     * 任务应直接进入 DEAD。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createNonRetryableOrder(String orderNo, String buyerId) {
        String bizId = "NON_RETRYABLE-" + orderNo;
        log.info("Creating non-retryable demo order: orderNo={}, buyerId={}", bizId, buyerId);
        return submitShipmentTask(bizId, buyerId);
    }

    /**
     * 重复投递示例。
     *
     * <p>同一显式 idempotencyKey 永久只保留一条任务，第二次投递返回同一个 taskId。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> createDuplicateOrder(String orderNo, String buyerId) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("firstTaskId", submitShipmentTask(orderNo, buyerId));
        result.put("secondTaskId", submitShipmentTask(orderNo, buyerId));
        return result;
    }

    /**
     * 对象 payload 投递示例。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createOrderWithObjectPayload(String orderNo, String buyerId, String address) {
        ShipmentPayload payload = new ShipmentPayload(orderNo, buyerId, address, false);
        return taskTemplate.submit(TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId(orderNo)
                .idempotencyKey(shipmentIdempotencyKey(orderNo))
                .maxRetryCount(3)
                .retryIntervalMs(2000L)
                .build(), payload);
    }

    private String submitShipmentTask(String orderNo, String buyerId) {
        ShipmentPayload payload = new ShipmentPayload(orderNo, buyerId, "Shanghai demo address", false);
        return taskTemplate.submit(TaskSubmitRequest.builder()
                .taskType("CREATE_SHIPMENT")
                .bizType("ORDER")
                .bizId(orderNo)
                .idempotencyKey(shipmentIdempotencyKey(orderNo))
                .maxRetryCount(3)
                .retryIntervalMs(2000L)
                .build(), payload);
    }

    private String shipmentIdempotencyKey(String orderNo) {
        return "shipment:order:" + orderNo;
    }
}
