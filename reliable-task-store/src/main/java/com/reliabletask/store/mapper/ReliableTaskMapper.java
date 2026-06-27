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
 *
 * <p>本接口中的 @Select 方法主要服务 Admin 统计和故障分析，不参与任务抢占和状态更新。
 * 写路径的条件更新、租约 CAS 和批量查询仍集中在 MyBatisTaskStore 中，便于统一审查并发语义。
 */
@Mapper
public interface ReliableTaskMapper extends BaseMapper<ReliableTaskEntity> {

    @Select("SELECT status AS status, COUNT(*) AS count FROM reliable_task GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT task_type AS taskType, COUNT(*) AS count FROM reliable_task GROUP BY task_type")
    List<Map<String, Object>> countByTaskType();

    /**
     * 按任务类型统计失败排行。
     *
     * <p>调用方必须先经过 AdminQueryGuard 归一化时间窗口和 limit，避免无边界聚合查询。
     */
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

    /**
     * 按错误码统计失败排行。
     *
     * <p>空错误码统一折叠为 UNKNOWN，便于 Admin 页面把“未分类失败”作为一组展示。
     */
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

    /**
     * 按任务类型和错误码联合统计失败排行，用于定位“哪类任务因什么原因失败最多”。
     */
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
