package com.reliabletask.admin.metrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.store.entity.ReliableTaskEntity;
import com.reliabletask.store.mapper.ReliableTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务指标收集器
 *
 * <p>从数据库收集任务执行指标，支持:
 * <ul>
 *   <li>各状态任务数量统计</li>
 *   <li>今日新增/成功/失败任务数</li>
 *   <li>按 taskType 分组统计</li>
 * </ul>
 *
 * <p>所有统计均为实时查询，不依赖缓存。
 * MVP 方案，数据量大时建议引入定时汇总或缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMetricsCollector {

    private final ReliableTaskMapper taskMapper;

    /**
     * 收集任务统计指标
     *
     * @return 完整的 TaskStatsVO 对象
     */
    public TaskStatsVO collectStats() {
        TaskStatsVO stats = new TaskStatsVO();

        // 1. 各状态任务数量
        Map<Integer, Long> statusCount = parseStatusCount(taskMapper.countByStatus());

        stats.setStatusCount(statusCount);
        stats.setTotalTasks(statusCount.values().stream().mapToLong(Long::longValue).sum());
        stats.setPendingTasks(statusCount.getOrDefault(TaskStatus.PENDING.getCode(), 0L)
                + statusCount.getOrDefault(TaskStatus.RETRYING.getCode(), 0L));
        stats.setDeadTasks(statusCount.getOrDefault(TaskStatus.DEAD.getCode(), 0L));

        // 2. 今日统计
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        Long todayNew = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .ge(ReliableTaskEntity::getCreateTime, todayStart)
        );
        stats.setTodayNewTasks(todayNew);

        Long todaySuccess = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.SUCCESS.getCode())
                        .ge(ReliableTaskEntity::getFinishTime, todayStart)
        );
        stats.setTodaySuccessTasks(todaySuccess);

        Long todayFailed = taskMapper.selectCount(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .eq(ReliableTaskEntity::getStatus, TaskStatus.DEAD.getCode())
                        .ge(ReliableTaskEntity::getFinishTime, todayStart)
        );
        stats.setTodayFailedTasks(todayFailed);

        // 3. 按 taskType 分组统计
        stats.setOldestPendingAgeSeconds(resolveOldestPendingAgeSeconds());
        stats.setTaskTypeStats(parseTaskTypeCount(taskMapper.countByTaskType()));

        return stats;
    }

    private Map<Integer, Long> parseStatusCount(List<Map<String, Object>> statusRows) {
        Map<Integer, Long> statusCount = new HashMap<>();
        for (Map<String, Object> row : statusRows) {
            Object statusObj = row.get("status");
            if (statusObj != null) {
                Integer code = ((Number) statusObj).intValue();
                statusCount.put(code, extractCount(row));
            }
        }
        return statusCount;
    }

    private Map<String, Long> parseTaskTypeCount(List<Map<String, Object>> typeRows) {
        Map<String, Long> taskTypeStats = new HashMap<>();
        for (Map<String, Object> row : typeRows) {
            Object typeObj = row.get("taskType");
            if (typeObj != null) {
                taskTypeStats.put(typeObj.toString(), extractCount(row));
            }
        }
        return taskTypeStats;
    }

    private long extractCount(Map<String, Object> row) {
        Object countObj = row.get("count");
        if (countObj == null) {
            countObj = row.get("COUNT(*)");
        }
        if (countObj == null) {
            return 0L;
        }
        return ((Number) countObj).longValue();
    }

    private long resolveOldestPendingAgeSeconds() {
        ReliableTaskEntity oldest = taskMapper.selectOne(
                new LambdaQueryWrapper<ReliableTaskEntity>()
                        .in(ReliableTaskEntity::getStatus,
                                TaskStatus.PENDING.getCode(), TaskStatus.RETRYING.getCode())
                        .orderByAsc(ReliableTaskEntity::getCreateTime)
                        .last("LIMIT 1")
        );
        if (oldest == null || oldest.getCreateTime() == null) {
            return 0L;
        }
        return Math.max(Duration.between(oldest.getCreateTime(), LocalDateTime.now()).getSeconds(), 0L);
    }
}
