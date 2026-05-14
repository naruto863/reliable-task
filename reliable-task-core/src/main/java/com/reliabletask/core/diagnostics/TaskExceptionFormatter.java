package com.reliabletask.core.diagnostics;

/**
 * 异常诊断格式化 SPI。
 *
 * <p>业务方可替换默认实现，对异常信息做脱敏、归类或压缩，避免主表和日志保存过长或敏感内容。
 */
@FunctionalInterface
public interface TaskExceptionFormatter {

    TaskFailureDiagnostic format(Throwable error);
}
