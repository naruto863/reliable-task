package com.reliabletask.core.lifecycle;

import com.reliabletask.core.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskStateMachine 测试")
class TaskStateMachineTest {

    @Test
    @DisplayName("canTransit - 允许 Worker 主生命周期流转")
    void canTransit_workerLifecycle_returnsTrue() {
        assertTrue(TaskStateMachine.canTransit(TaskStatus.PENDING, TaskStatus.RUNNING));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RETRYING, TaskStatus.RUNNING));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RUNNING, TaskStatus.SUCCESS));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RUNNING, TaskStatus.RETRYING));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RUNNING, TaskStatus.DEAD));
    }

    @Test
    @DisplayName("canTransit - 允许人工重新入队 DEAD/CANCELLED")
    void canTransit_manualRequeue_returnsTrue() {
        assertTrue(TaskStateMachine.canTransit(TaskStatus.DEAD, TaskStatus.PENDING));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.CANCELLED, TaskStatus.PENDING));
    }

    @Test
    @DisplayName("canTransit - 拒绝终态自动重新执行")
    void canTransit_terminalAutoRestart_returnsFalse() {
        assertFalse(TaskStateMachine.canTransit(TaskStatus.SUCCESS, TaskStatus.PENDING));
        assertFalse(TaskStateMachine.canTransit(TaskStatus.SUCCESS, TaskStatus.RUNNING));
        assertFalse(TaskStateMachine.canTransit(TaskStatus.DEAD, TaskStatus.RUNNING));
        assertFalse(TaskStateMachine.canTransit(TaskStatus.CANCELLED, TaskStatus.RUNNING));
    }

    @Test
    @DisplayName("requireTransit - 非法流转抛出异常")
    void requireTransit_illegalTransition_throwsException() {
        assertDoesNotThrow(() -> TaskStateMachine.requireTransit(TaskStatus.PENDING, TaskStatus.RUNNING));

        assertThrows(IllegalStateException.class,
                () -> TaskStateMachine.requireTransit(TaskStatus.SUCCESS, TaskStatus.RETRYING));
    }
}
