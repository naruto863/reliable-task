package com.reliabletask.core.vo;

import lombok.Data;

/**
 * 控制台 payload 安全视图。
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
     */
    private String payloadPlaintext;
}
