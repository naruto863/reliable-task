package com.reliabletask.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskStatus 枚举测试")
class TaskStatusTest {

    @Test
    @DisplayName("fromCode - 正常值转换")
    void fromCode_validCode_returnsCorrectStatus() {
        assertEquals(TaskStatus.PENDING, TaskStatus.fromCode(0));
        assertEquals(TaskStatus.RUNNING, TaskStatus.fromCode(1));
        assertEquals(TaskStatus.SUCCESS, TaskStatus.fromCode(2));
        assertEquals(TaskStatus.FAILED, TaskStatus.fromCode(3));
        assertEquals(TaskStatus.RETRYING, TaskStatus.fromCode(4));
        assertEquals(TaskStatus.DEAD, TaskStatus.fromCode(5));
        assertEquals(TaskStatus.CANCELLED, TaskStatus.fromCode(6));
    }

    @Test
    @DisplayName("fromCode - 非法值抛异常")
    void fromCode_invalidCode_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromCode(-1));
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromCode(7));
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromCode(100));
    }

    @Test
    @DisplayName("isTerminal - SUCCESS 和 CANCELLED 是终态")
    void isTerminal_terminalStatus_returnsTrue() {
        assertTrue(TaskStatus.SUCCESS.isTerminal());
        assertTrue(TaskStatus.CANCELLED.isTerminal());
    }

    @Test
    @DisplayName("isTerminal - 非终态返回 false")
    void isTerminal_nonTerminal_returnsFalse() {
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
        assertFalse(TaskStatus.FAILED.isTerminal());
        assertFalse(TaskStatus.RETRYING.isTerminal());
        assertFalse(TaskStatus.DEAD.isTerminal());
    }

    @Test
    @DisplayName("isExecutable - PENDING 和 RETRYING 可执行")
    void isExecutable_executableStatus_returnsTrue() {
        assertTrue(TaskStatus.PENDING.isExecutable());
        assertTrue(TaskStatus.RETRYING.isExecutable());
    }

    @Test
    @DisplayName("isExecutable - 非可执行状态返回 false")
    void isExecutable_nonExecutable_returnsFalse() {
        assertFalse(TaskStatus.RUNNING.isExecutable());
        assertFalse(TaskStatus.SUCCESS.isExecutable());
        assertFalse(TaskStatus.FAILED.isExecutable());
        assertFalse(TaskStatus.DEAD.isExecutable());
        assertFalse(TaskStatus.CANCELLED.isExecutable());
    }
}
