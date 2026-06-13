package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.core.vo.FailureTopVO;
import com.reliabletask.store.entity.ReliableTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务主表 Mapper
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供基础 CRUD 能力。
 * 复杂查询（如拉取待执行任务、超时扫描等）通过 Wrapper 构建。
 */
@Mapper
public interface ReliableTaskMapper extends BaseMapper<ReliableTaskEntity> {

    @Select("SELECT status AS status, COUNT(*) AS count FROM reliable_task GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT task_type AS taskType, COUNT(*) AS count FROM reliable_task GROUP BY task_type")
    List<Map<String, Object>> countByTaskType();

    @Select("""
            <script>
            SELECT
                t.task_type AS taskType,
                COUNT(*) AS failureCount
            FROM reliable_task_log l
            INNER JOIN reliable_task t ON t.id = l.task_id
            WHERE l.status = #{failedStatus}
            <if test="taskType != null and taskType != ''">
                AND t.task_type = #{taskType}
            </if>
            <if test="createTimeStart != null">
                AND l.execute_time &gt;= #{createTimeStart}
            </if>
            <if test="createTimeEnd != null">
                AND l.execute_time &lt;= #{createTimeEnd}
            </if>
            GROUP BY t.task_type
            ORDER BY failureCount DESC, t.task_type ASC
            LIMIT #{limit}
            </script>
            """)
    List<FailureTopVO> selectFailureTopByTaskType(@Param("taskType") String taskType,
                                                  @Param("createTimeStart") LocalDateTime createTimeStart,
                                                  @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                                  @Param("limit") int limit,
                                                  @Param("failedStatus") int failedStatus);

    @Select("""
            <script>
            SELECT
                COALESCE(NULLIF(l.error_code, ''), 'UNKNOWN') AS errorCode,
                COUNT(*) AS failureCount
            FROM reliable_task_log l
            INNER JOIN reliable_task t ON t.id = l.task_id
            WHERE l.status = #{failedStatus}
            <if test="taskType != null and taskType != ''">
                AND t.task_type = #{taskType}
            </if>
            <if test="createTimeStart != null">
                AND l.execute_time &gt;= #{createTimeStart}
            </if>
            <if test="createTimeEnd != null">
                AND l.execute_time &lt;= #{createTimeEnd}
            </if>
            GROUP BY COALESCE(NULLIF(l.error_code, ''), 'UNKNOWN')
            ORDER BY failureCount DESC, errorCode ASC
            LIMIT #{limit}
            </script>
            """)
    List<FailureTopVO> selectFailureTopByErrorCode(@Param("taskType") String taskType,
                                                   @Param("createTimeStart") LocalDateTime createTimeStart,
                                                   @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                                   @Param("limit") int limit,
                                                   @Param("failedStatus") int failedStatus);

    @Select("""
            <script>
            SELECT
                t.task_type AS taskType,
                COALESCE(NULLIF(l.error_code, ''), 'UNKNOWN') AS errorCode,
                COUNT(*) AS failureCount
            FROM reliable_task_log l
            INNER JOIN reliable_task t ON t.id = l.task_id
            WHERE l.status = #{failedStatus}
            <if test="taskType != null and taskType != ''">
                AND t.task_type = #{taskType}
            </if>
            <if test="createTimeStart != null">
                AND l.execute_time &gt;= #{createTimeStart}
            </if>
            <if test="createTimeEnd != null">
                AND l.execute_time &lt;= #{createTimeEnd}
            </if>
            GROUP BY t.task_type, COALESCE(NULLIF(l.error_code, ''), 'UNKNOWN')
            ORDER BY failureCount DESC, t.task_type ASC, errorCode ASC
            LIMIT #{limit}
            </script>
            """)
    List<FailureTopVO> selectFailureTopByTaskTypeAndErrorCode(@Param("taskType") String taskType,
                                                              @Param("createTimeStart") LocalDateTime createTimeStart,
                                                              @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                                              @Param("limit") int limit,
                                                              @Param("failedStatus") int failedStatus);
}
