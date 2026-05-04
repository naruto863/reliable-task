package com.reliabletask.executor.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkerIdGenerator 测试")
class WorkerIdGeneratorTest {

    @Test
    @DisplayName("getWorkerId 返回非空字符串")
    void getWorkerId_returnsNonEmptyString() {
        String workerId = WorkerIdGenerator.getWorkerId();

        assertNotNull(workerId);
        assertFalse(workerId.isBlank());
    }

    @Test
    @DisplayName("getWorkerId 格式为 hostname:shortUUID")
    void getWorkerId_correctFormat() {
        String workerId = WorkerIdGenerator.getWorkerId();

        assertTrue(workerId.contains(":"), "Worker ID should contain ':' separator");
        String[] parts = workerId.split(":");
        assertEquals(2, parts.length, "Worker ID should have exactly 2 parts");
        assertEquals(8, parts[1].length(), "UUID part should be 8 characters");
    }

    @Test
    @DisplayName("getWorkerId 多次调用返回相同值")
    void getWorkerId_returnsSameValue() {
        String id1 = WorkerIdGenerator.getWorkerId();
        String id2 = WorkerIdGenerator.getWorkerId();

        assertSame(id1, id2);
    }
}
