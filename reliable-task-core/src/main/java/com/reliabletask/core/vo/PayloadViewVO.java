package com.reliabletask.core.vo;

import lombok.Data;

/**
 * 控制台 payload 安全视图。
 *
 * <p>通过显式字段描述“是否存在、是否遮蔽、是否允许明文、预览长度”等状态，
 * 避免前端通过判断 payload 字符串是否为空来推断安全策略。
 */
@Data
public class PayloadViewVO {

    /**
     * 当前任务是否存在 payload。
     */
    private boolean payloadVisible;

    /**
     * preview 是否已遮蔽。
     */
    private boolean payloadMasked;

    /**
     * 截断或遮蔽后的 payload preview。
     *
     * <p>该字段适合默认展示，不应被调用方当作可重新提交的完整 payload。
     */
    private String payloadPreview;

    /**
     * 原始 payload 字符长度。
     */
    private int payloadLength;

    /**
     * 是否允许控制台 reveal payload 明文。
     */
    private boolean payloadRevealAllowed;

    /**
     * 显式允许时返回的 payload 明文；默认保持 null。
     *
     * <p>只有在配置允许且当前请求路径被授权时才应填充，避免控制台默认暴露敏感业务数据。
     */
    private String payloadPlaintext;
}
