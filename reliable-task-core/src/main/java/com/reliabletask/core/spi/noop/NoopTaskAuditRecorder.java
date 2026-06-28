package com.reliabletask.core.spi.noop;

import com.reliabletask.core.model.AuditLog;
import com.reliabletask.core.spi.TaskAuditRecorder;

/**
 * 默认审计实现：忽略审计事件。
 *
 * <p>这是兼容 fallback，不是生产审计能力。Admin 写操作若要求审计，自动配置层应注入真实 recorder，
 * 否则控制台能力会把写入口降级为不可用。
 */
public class NoopTaskAuditRecorder implements TaskAuditRecorder {

    @Override
    public void record(AuditLog auditLog) {
        // no-op
    }
}
