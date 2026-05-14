package com.reliabletask.core.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultTaskExceptionFormatter 测试")
class DefaultTaskExceptionFormatterTest {

    @Test
    @DisplayName("format - 提取根因、错误码和压缩堆栈")
    void format_extractsRootCauseAndStackTrace() {
        TaskExceptionFormatter formatter = new DefaultTaskExceptionFormatter(64, 512);

        TaskFailureDiagnostic diagnostic = formatter.format(
                new CompletionException(new IllegalStateException("external service unavailable")));

        assertEquals("IllegalStateException", diagnostic.getErrorCode());
        assertEquals("external service unavailable", diagnostic.getErrorMessage());
        assertNotNull(diagnostic.getStackTrace());
        assertTrue(diagnostic.getStackTrace().contains("IllegalStateException"));
        assertTrue(diagnostic.getStackTrace().length() <= 512);
    }

    @Test
    @DisplayName("format - 空 message 时使用异常类型")
    void format_emptyMessage_usesExceptionType() {
        TaskExceptionFormatter formatter = new DefaultTaskExceptionFormatter();

        TaskFailureDiagnostic diagnostic = formatter.format(new NullPointerException());

        assertEquals("NullPointerException", diagnostic.getErrorCode());
        assertEquals("NullPointerException", diagnostic.getErrorMessage());
    }
}
