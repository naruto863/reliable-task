package com.reliabletask.core.vo;

import lombok.Data;

/**
 * 控制台能力视图对象。
 *
 * <p>用于前端控制台判断只读、写操作、审计、批量和 payload 展示能力。
 *
 * <p>这是 console-safe contract 的入口：前端只根据这些能力开关展示按钮和提示，
 * 后端 Controller 仍会在每个写接口上再次执行 write gate、auth、audit 和确认头校验。
 */
@Data
public class ConsoleCapabilitiesVO {

    /**
     * Admin API 是否已注册。
     */
    private boolean adminEnabled;

    /**
     * Admin 写操作总开关。
     *
     * <p>为 true 不代表写按钮一定可用，还需要 authEnabled、auditEnabled 以及具体功能开关配合。
     */
    private boolean writeEnabled;

    /**
     * Admin 权限检查是否启用。
     */
    private boolean authEnabled;

    /**
     * Admin 审计是否启用。
     */
    private boolean auditEnabled;

    /**
     * Admin 批量操作是否启用。
     */
    private boolean batchEnabled;

    /**
     * 最大列表分页大小。
     */
    private int maxPageSize;

    /**
     * 最大批量操作 limit。
     */
    private int maxBatchLimit;

    /**
     * 控制台是否允许返回 payload 明文。
     */
    private boolean payloadPlaintextEnabled;

    /**
     * 控制台是否允许 reveal payload。
     *
     * <p>该值用于 UI 告知能力边界；真正是否返回明文还取决于 payloadPlaintextEnabled
     * 和单个任务详情构造出的 PayloadViewVO。
     */
    private boolean payloadRevealAllowed;

    /**
     * payload preview 最大长度。
     */
    private int payloadPreviewLength;

    /**
     * 控制台写操作是否要求确认 header。
     */
    private boolean writeConfirmationRequired;
}
