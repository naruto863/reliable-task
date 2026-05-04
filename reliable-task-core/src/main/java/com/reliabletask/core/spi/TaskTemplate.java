package com.reliabletask.core.spi;

import com.reliabletask.core.model.TaskInstance;
import com.reliabletask.core.model.TaskSubmitRequest;
import com.reliabletask.core.model.TaskSubmitResult;

import java.time.Duration;
import java.util.List;

/**
 * 任务投递模板接口
 *
 * <p>业务方通过此接口向 ReliableTask 组件投递异步任务。
 * 支持立即投递、延迟投递和批量投递三种模式。
 *
 * <p>典型使用场景:
 * <pre>
 * // 在事务方法中投递，任务会与业务数据处于同一个本地事务
 * &#64;Transactional
 * public void createOrder(OrderRequest req) {
 *     orderMapper.insert(order);
 *     taskTemplate.submit(TaskSubmitRequest.builder()
 *         .taskType("SEND_EMAIL")
 *         .bizType("ORDER")
 *         .bizId(order.getOrderNo())
 *         .payload("{\"to\":\"user@example.com\"}")
 *         .build());
 * }
 * </pre>
 */
public interface TaskTemplate {

    /**
     * 立即投递任务
     *
     * <p>如果当前在事务中，任务会在当前事务内直接写入；
     * 如果不在事务中，任务会立即写入。
     *
     * @param request 投递请求
     * @return 任务 ID 字符串；如果存储层返回空 ID，则回退为业务唯一标识 bizId
     */
    String submit(TaskSubmitRequest request);

    /**
     * 立即投递对象 payload 任务。
     *
     * <p>实现会在入库前将 payload 序列化为字符串。
     *
     * @param request 投递请求
     * @param payload 对象 payload
     * @return 任务 ID 字符串；如果存储层返回空 ID，则回退为业务唯一标识 bizId
     */
    String submit(TaskSubmitRequest request, Object payload);

    /**
     * 立即投递任务并返回结构化投递结果。
     *
     * @param request 投递请求
     * @return 结构化投递结果，可区分新建任务和幂等命中已有任务
     */
    TaskSubmitResult submitForResult(TaskSubmitRequest request);

    /**
     * 立即投递对象 payload 任务并返回结构化投递结果。
     *
     * @param request 投递请求
     * @param payload 对象 payload
     * @return 结构化投递结果，可区分新建任务和幂等命中已有任务
     */
    TaskSubmitResult submitForResult(TaskSubmitRequest request, Object payload);

    /**
     * 延迟投递任务
     *
     * <p>任务会在指定延迟后变为可执行状态。
     * 投递行为与 submit 相同。
     *
     * @param request 投递请求
     * @param delay   延迟时间
     * @return 任务 ID 字符串；如果存储层返回空 ID，则回退为业务唯一标识 bizId
     */
    String submitDelay(TaskSubmitRequest request, Duration delay);

    /**
     * 批量投递任务
     *
     * <p>批量投递的行为与单个投递相同（事务感知）。
     * 每个任务的 bizUniqueKey 独立判断幂等。
     *
     * @param requests 投递请求列表
     * @return 每个请求对应的任务 ID 字符串列表（顺序与输入一致）
     */
    List<String> submitBatch(List<TaskSubmitRequest> requests);
}
