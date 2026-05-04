package com.reliabletask.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reliabletask.store.entity.ReliableTaskAuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Admin 操作审计表 Mapper。
 */
@Mapper
public interface ReliableTaskAuditLogMapper extends BaseMapper<ReliableTaskAuditLogEntity> {
}
