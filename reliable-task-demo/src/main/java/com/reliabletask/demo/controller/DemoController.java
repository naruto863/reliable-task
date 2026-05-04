package com.reliabletask.demo.controller;

import com.reliabletask.admin.model.Result;
import com.reliabletask.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示接口
 *
 * <p>提供简单的下单接口，用于演示 ReliableTask 的完整流程。
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final OrderService orderService;

    /**
     * 创建订单并投递发货任务，演示失败自动重试后成功。
     *
     * <p>调用示例: POST /demo/order?orderNo=ORD-001&buyerId=USER-123
     */
    @PostMapping("/order")
    public Result<String> createOrder(
            @RequestParam(defaultValue = "ORD-001") String orderNo,
            @RequestParam(defaultValue = "USER-123") String buyerId) {

        log.info("Demo: Creating order - orderNo={}, buyerId={}", orderNo, buyerId);

        String result = orderService.createOrder(orderNo, buyerId);

        return Result.success(result);
    }

    /**
     * 创建不可重试任务，演示 NonRetryableException 直接进入 DEAD。
     */
    @PostMapping("/order/non-retryable")
    public Result<String> createNonRetryableOrder(
            @RequestParam(defaultValue = "ORD-BAD-001") String orderNo,
            @RequestParam(defaultValue = "USER-123") String buyerId) {

        String taskId = orderService.createNonRetryableOrder(orderNo, buyerId);
        return Result.success(taskId);
    }

    /**
     * 重复投递同一业务任务，演示 V1.5 幂等键 MVP 行为。
     */
    @PostMapping("/order/duplicate")
    public Result<Map<String, String>> createDuplicateOrder(
            @RequestParam(defaultValue = "ORD-DUP-001") String orderNo,
            @RequestParam(defaultValue = "USER-123") String buyerId) {

        return Result.success(orderService.createDuplicateOrder(orderNo, buyerId));
    }

    /**
     * 对象 payload 投递示例。
     */
    @PostMapping("/order/object-payload")
    public Result<String> createOrderWithObjectPayload(
            @RequestParam(defaultValue = "ORD-OBJ-001") String orderNo,
            @RequestParam(defaultValue = "USER-123") String buyerId,
            @RequestParam(defaultValue = "Shanghai demo address") String address) {

        return Result.success(orderService.createOrderWithObjectPayload(orderNo, buyerId, address));
    }
}
