package com.reliabletask.core.model;

import com.reliabletask.core.enums.TaskEventType;
import com.reliabletask.core.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务状态变更事件。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {

    private TaskEventType eventType;
    private Long taskId;
    private String taskType;
    private String bizType;
    private String bizId;
    private TaskStatus statusBefore;
    private TaskStatus statusAfter;
    private String reason;
    private String workerId;
    private String traceId;
    private LocalDateTime eventTime;

    public static TaskEvent of(TaskEventType eventType,
                               TaskInstance task,
                               TaskStatus statusBefore,
                               TaskStatus statusAfter,
                               String reason) {
        TaskEventBuilder builder = TaskEvent.builder()
                .eventType(eventType)
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .reason(reason)
                .eventTime(LocalDateTime.now());
        if (task != null) {
            builder.taskId(task.getId())
                    .taskType(task.getTaskType())
                    .bizType(task.getBizType())
                    .bizId(task.getBizId())
                    .workerId(task.getWorkerId())
                    .traceId(task.getTraceId());
        }
        return builder.build();
    }
}
