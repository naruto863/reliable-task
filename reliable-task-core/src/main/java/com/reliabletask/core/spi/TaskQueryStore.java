package com.reliabletask.core.spi;

import com.reliabletask.core.dto.FailureTopQueryRequest;
import com.reliabletask.core.dto.PageResult;
import com.reliabletask.core.dto.SlowTaskQueryRequest;
import com.reliabletask.core.dto.TaskFailureQueryRequest;
import com.reliabletask.core.dto.TaskQueryRequest;
import com.reliabletask.core.vo.FailureTopVO;
import com.reliabletask.core.vo.SlowTaskVO;
import com.reliabletask.core.vo.TaskDetailVO;
import com.reliabletask.core.vo.TaskFailureVO;
import com.reliabletask.core.vo.TaskLogVO;
import com.reliabletask.core.vo.TaskStatsVO;
import com.reliabletask.core.vo.TaskTimelineItemVO;
import com.reliabletask.core.vo.TaskVO;

import java.util.List;

/**
 * 任务查询存储 SPI。
 *
 * <p>覆盖 Admin 只读查询、任务详情、执行日志、统计和 v0.5 运维分析视图。
 * Admin 查询、metrics 和 alert 读取路径应优先依赖本接口。
 */
public interface TaskQueryStore {

    /**
     * 分页查询任务列表。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<TaskVO> listTasks(TaskQueryRequest request);

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    TaskDetailVO getTaskDetail(Long id);

    /**
     * 查询任务执行日志。
     *
     * @param taskId 任务 ID
     * @return 执行日志列表
     */
    List<TaskLogVO> getTaskLogs(Long taskId);

    /**
     * 获取任务统计。
     *
     * @return 各状态任务数量及按 taskType 分组统计
     */
    TaskStatsVO getStats();

    /**
     * 查询最近失败执行记录。
     *
     * @param request 查询条件
     * @return 最近失败记录
     */
    default List<TaskFailureVO> listRecentFailures(TaskFailureQueryRequest request) {
        return List.of();
    }

    /**
     * 查询慢执行任务记录。
     *
     * @param request 查询条件
     * @return 慢任务记录
     */
    default List<SlowTaskVO> listSlowTasks(SlowTaskQueryRequest request) {
        return List.of();
    }

    /**
     * 查询失败 Top 聚合。
     *
     * @param request 查询条件
     * @return 失败 Top 聚合结果
     */
    default List<FailureTopVO> listFailureTop(FailureTopQueryRequest request) {
        return List.of();
    }

    /**
     * 查询任务生命周期时间线。
     *
     * @param taskId 任务 ID
     * @return 任务生命周期时间线
     */
    default List<TaskTimelineItemVO> getTaskTimeline(Long taskId) {
        return List.of();
    }
}
