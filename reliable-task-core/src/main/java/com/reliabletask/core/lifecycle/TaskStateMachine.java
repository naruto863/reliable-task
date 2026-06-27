package com.reliabletask.core.lifecycle;

import com.reliabletask.core.enums.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态机。
 *
 * <p>集中定义主生命周期和人工干预允许的状态流转，避免存储层和执行层各自散落状态判断。
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        /*
         * 这里只声明“业务上允许”的状态边，不负责并发裁决。
         * 实际更新仍必须在存储层用 WHERE status / lease / version 等条件保证原子性。
         */
        TRANSITIONS.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        TRANSITIONS.put(TaskStatus.RETRYING, EnumSet.of(TaskStatus.RUNNING, TaskStatus.DEAD, TaskStatus.CANCELLED));
        TRANSITIONS.put(TaskStatus.RUNNING, EnumSet.of(
                TaskStatus.SUCCESS, TaskStatus.RETRYING, TaskStatus.DEAD, TaskStatus.CANCELLED, TaskStatus.PENDING));
        TRANSITIONS.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.CANCELLED));
        TRANSITIONS.put(TaskStatus.DEAD, EnumSet.of(TaskStatus.PENDING));
        TRANSITIONS.put(TaskStatus.CANCELLED, EnumSet.of(TaskStatus.PENDING));
    }

    private TaskStateMachine() {
    }

    public static boolean canTransit(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransit(TaskStatus from, TaskStatus to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateException("Illegal task status transition: " + from + " -> " + to);
        }
    }
}
