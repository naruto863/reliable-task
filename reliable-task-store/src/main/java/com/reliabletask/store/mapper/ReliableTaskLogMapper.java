package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.store.entity.ReliableTaskLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行日志表 Mapper
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供基础 CRUD 能力。
 */
@Mapper
public interface ReliableTaskLogMapper extends BaseMapper<ReliableTaskLogEntity> {
}
