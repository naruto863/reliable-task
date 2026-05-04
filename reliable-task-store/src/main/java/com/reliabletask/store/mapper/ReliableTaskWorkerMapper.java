package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.store.entity.ReliableTaskWorkerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Worker 心跳表 Mapper。
 */
@Mapper
public interface ReliableTaskWorkerMapper extends BaseMapper<ReliableTaskWorkerEntity> {
}
