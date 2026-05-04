package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.spi.TaskAuditRecorder;

/**
 * 默认审计实现：忽略审计事件。
 */
public class NoopTaskAuditRecorder implements TaskAuditRecorder {

    @Override
    public void record(AuditLog auditLog) {
        // no-op
    }
}
