package com.reliabletask.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demo 发货任务对象 payload。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentPayload {

    private String orderNo;

    private String buyerId;

    private String address;

    private boolean forceNonRetryable;
}
