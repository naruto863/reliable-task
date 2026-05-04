package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.store.entity.ReliableTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
