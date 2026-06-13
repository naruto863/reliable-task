package com.reliabletask.core.spi;

/**
 * 上下文感知的任务 payload 编解码 SPI。
 *
 * <p>相较于旧的 {@link TaskPayloadSerializer}，codec 在序列化和反序列化时可以读取
 * taskType、bizId、tenantId、traceId 等任务上下文。默认实现仍保持与旧 serializer
 * 相同的入库字符串格式，不引入压缩、加密或 schema version。
 */
public interface TaskPayloadCodec {

    /**
     * 将业务 payload 编码为入库字符串。
     *
     * @param payload 业务 payload
     * @param context 编码上下文
     * @return 入库 payload 字符串
     */
    String encode(Object payload, TaskPayloadCodecContext context);

    /**
     * 将入库 payload 字符串解码为目标类型。
     *
     * @param payload    入库 payload 字符串
     * @param targetType 目标类型
     * @param context    解码上下文
     * @param <T>        目标类型
     * @return 解码结果
     */
    <T> T decode(String payload, Class<T> targetType, TaskPayloadCodecContext context);
}
