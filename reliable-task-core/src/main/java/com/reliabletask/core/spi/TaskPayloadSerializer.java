package com.reliabletask.core.spi;

/**
 * 任务 payload 序列化 SPI。
 */
public interface TaskPayloadSerializer {

    /**
     * 将业务 payload 序列化为入库字符串。
     *
     * @param payload 业务 payload
     * @return 入库 payload 字符串
     */
    String serialize(Object payload);

    /**
     * 将入库 payload 字符串反序列化为目标类型。
     *
     * @param payload    入库 payload 字符串
     * @param targetType 目标类型
     * @param <T>        目标类型
     * @return 反序列化结果
     */
    <T> T deserialize(String payload, Class<T> targetType);
}
