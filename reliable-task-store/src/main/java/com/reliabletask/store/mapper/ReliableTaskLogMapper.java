package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.core.vo.SlowTaskVO;
import com.reliabletask.core.vo.TaskFailureVO;
import com.reliabletask.store.entity.ReliableTaskLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行日志表 Mapper
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供基础 CRUD 能力。
 */
@Mapper
public interface ReliableTaskLogMapper extends BaseMapper<ReliableTaskLogEntity> {

    @Select("""
            <script>
            SELECT
                l.task_id AS taskId,
                t.task_type AS taskType,
                t.biz_type AS bizType,
                t.biz_id AS bizId,
                l.status_after AS statusAfter,
                l.error_code AS errorCode,
                l.error_msg AS errorMsg,
                l.duration_ms AS durationMs,
                l.worker_id AS workerId,
                l.trace_id AS traceId,
                l.execute_time AS executeTime
            FROM reliable_task_log l
            INNER JOIN reliable_task t ON t.id = l.task_id
            WHERE l.status = #{failedStatus}
            <if test="taskType != null and taskType != ''">
                AND t.task_type = #{taskType}
            </if>
            <if test="errorCode != null and errorCode != ''">
                AND l.error_code = #{errorCode}
            </if>
            <if test="createTimeStart != null">
                AND l.execute_time &gt;= #{createTimeStart}
            </if>
            <if test="createTimeEnd != null">
                AND l.execute_time &lt;= #{createTimeEnd}
            </if>
            ORDER BY l.execute_time DESC, l.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<TaskFailureVO> selectRecentFailures(@Param("taskType") String taskType,
                                             @Param("errorCode") String errorCode,
                                             @Param("createTimeStart") LocalDateTime createTimeStart,
                                             @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                             @Param("limit") int limit,
                                             @Param("failedStatus") int failedStatus);

    @Select("""
            <script>
            SELECT
                l.task_id AS taskId,
                t.task_type AS taskType,
                t.biz_type AS bizType,
                t.biz_id AS bizId,
                l.status_before AS statusBefore,
                l.status_after AS statusAfter,
                l.error_code AS errorCode,
                l.error_msg AS errorMsg,
                l.duration_ms AS durationMs,
                l.worker_id AS workerId,
                l.trace_id AS traceId,
                l.execute_time AS executeTime
            FROM reliable_task_log l
            INNER JOIN reliable_task t ON t.id = l.task_id
            WHERE l.duration_ms &gt;= #{durationMsGte}
            <if test="taskType != null and taskType != ''">
                AND t.task_type = #{taskType}
            </if>
            <if test="createTimeStart != null">
                AND l.execute_time &gt;= #{createTimeStart}
            </if>
            <if test="createTimeEnd != null">
                AND l.execute_time &lt;= #{createTimeEnd}
            </if>
            ORDER BY l.duration_ms DESC, l.execute_time DESC, l.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<SlowTaskVO> selectSlowTasks(@Param("taskType") String taskType,
                                     @Param("durationMsGte") long durationMsGte,
                                     @Param("createTimeStart") LocalDateTime createTimeStart,
                                     @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                     @Param("limit") int limit);
}
