package com.reliabletask.executor.interceptor;

import com.reliabletask.core.model.TaskInstance;
import lombok.Builder;
import lombok.Value;

/**
 * TaskInterceptor 使用的执行上下文。
 */
@Value
@Builder
public class TaskExecutionContext {

    TaskInstance task;
    Long taskId;
    String taskType;
    String bizType;
    String bizId;
    String workerId;
    String traceId;

    public static TaskExecutionContext from(TaskInstance task) {
        if (task == null) {
            return empty();
        }
        return TaskExecutionContext.builder()
                .task(task)
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .workerId(task.getWorkerId())
                .traceId(task.getTraceId())
                .build();
    }

    public static TaskExecutionContext empty() {
        return TaskExecutionContext.builder().build();
    }
}
