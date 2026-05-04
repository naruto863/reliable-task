package com.reliabletask.admin.metrics;

import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("TaskMetricsCollector 测试")
@ExtendWith(MockitoExtension.class)
class TaskMetricsCollectorTest {

    @Mock
    private ReliableTaskMapper taskMapper;

    @Test
    @DisplayName("collectStats - 正确聚合状态、今日指标与任务类型")
    void collectStats_aggregatesAllStatsCorrectly() {
        when(taskMapper.countByStatus())
                .thenReturn(List.of(
                        Map.of("status", TaskStatus.PENDING.getCode(), "count", 1L),
                        Map.of("status", TaskStatus.RETRYING.getCode(), "count", 1L),
                        Map.of("status", TaskStatus.DEAD.getCode(), "count", 2L)
                ));
        when(taskMapper.countByTaskType())
                .thenReturn(List.of(
                        Map.of("taskType", "CREATE_SHIPMENT", "count", 2L),
                        Map.of("taskType", "SYNC_ORDER", "count", 1L)
                ));

        when(taskMapper.selectCount(any()))
                .thenReturn(12L)  // todayNew
                .thenReturn(8L)   // todaySuccess
                .thenReturn(2L);  // todayFailed

        TaskMetricsCollector collector = new TaskMetricsCollector(taskMapper);
        TaskStatsVO stats = collector.collectStats();

        assertEquals(4L, stats.getTotalTasks());
        assertEquals(2L, stats.getPendingTasks());
        assertEquals(2L, stats.getDeadTasks());

        assertEquals(1L, stats.getStatusCount().get(TaskStatus.PENDING.getCode()));
        assertEquals(1L, stats.getStatusCount().get(TaskStatus.RETRYING.getCode()));
        assertEquals(2L, stats.getStatusCount().get(TaskStatus.DEAD.getCode()));

        assertEquals(12L, stats.getTodayNewTasks());
        assertEquals(8L, stats.getTodaySuccessTasks());
        assertEquals(2L, stats.getTodayFailedTasks());

        assertEquals(2L, stats.getTaskTypeStats().get("CREATE_SHIPMENT"));
        assertEquals(1L, stats.getTaskTypeStats().get("SYNC_ORDER"));
    }

    @Test
    @DisplayName("collectStats - 忽略 null 状态与 null taskType")
    void collectStats_ignoresNullStatusAndTaskType() {
        Map<String, Object> nullStatus = new HashMap<>();
        nullStatus.put("status", null);
        Map<String, Object> nullType = new HashMap<>();
        nullType.put("taskType", null);

        when(taskMapper.countByStatus())
                .thenReturn(List.of(
                        Map.of("status", TaskStatus.PENDING.getCode(), "count", 1L),
                        nullStatus
                ));
        when(taskMapper.countByTaskType())
                .thenReturn(List.of(
                        Map.of("taskType", "TYPE-A", "count", 1L),
                        nullType
                ));

        when(taskMapper.selectCount(any())).thenReturn(1L, 0L, 0L);

        TaskMetricsCollector collector = new TaskMetricsCollector(taskMapper);
        TaskStatsVO stats = collector.collectStats();

        assertEquals(1L, stats.getTotalTasks());
        assertEquals(1L, stats.getPendingTasks());
        assertFalse(stats.getStatusCount().containsKey(null));

        assertEquals(1L, stats.getTaskTypeStats().get("TYPE-A"));
        assertFalse(stats.getTaskTypeStats().containsKey(null));
    }
}
