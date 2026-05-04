package com.reliabletask.core.spi;

import com.reliabletask.core.model.AuditLog;

/**
 * 审计记录 SPI。
 */
public interface TaskAuditRecorder {

    /**
     * 记录审计日志。
     *
     * @param auditLog 审计日志
     */
    void record(AuditLog auditLog);
}
