package com.reliabletask.core.spi;

/**
 * 任务序列化器 SPI
 *
 * <p>负责 TaskInstance 中 payload 字段的序列化与反序列化。
 * 默认实现基于 JSON（Jackson），业务方可替换为其他序列化方案
 * （如 Protobuf、Kryo 等）以实现更高的性能或更小的存储体积。
 *
 * <p>序列化范围:
 * <ul>
 *   <li>仅序列化 payload 业务数据，不序列化整个 TaskInstance</li>
 *   <li>payload 在投递时序列化后存入数据库</li>
 *   <li>payload 在执行时从数据库反序列化后传给 Handler</li>
 * </ul>
 */
public interface TaskSerializer {

    /**
     * 将对象序列化为 JSON 字符串
     *
     * <p>用于投递阶段: 业务方传入的 payload 对象 → 存入数据库的 JSON 字符串
     *
     * @param obj 待序列化的对象，不可为 null
     * @return JSON 格式字符串
     * @throws RuntimeException 序列化失败时抛出
     */
    String serialize(Object obj);

    /**
     * 将 JSON 字符串反序列化为指定类型的对象
     *
     * <p>用于执行阶段: 从数据库读取的 JSON 字符串 → 传给 Handler 的业务对象
     *
     * @param json  JSON 格式字符串，不可为 null
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化后的对象
     * @throws RuntimeException 反序列化失败时抛出
     */
    <T> T deserialize(String json, Class<T> clazz);
}
