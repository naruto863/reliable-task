package com.reliabletask.store.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行日志表 Entity
 *
 * <p>对应数据库表 reliable_task_log，记录任务每次执行的详细结果。
 */
@Data
@TableName("reliable_task_log")
public class ReliableTaskLogEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的任务主表 ID
     */
    private Long taskId;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 执行耗时，单位毫秒
     */
    private Long durationMs;

    /**
     * 执行结果: 2-SUCCESS 3-FAILED
     */
    private Integer status;

    /**
     * 错误码或异常类型
     */
    private String errorCode;

    /**
     * 异常堆栈信息
     */
    private String errorMsg;

    /**
     * 执行节点 ID
     */
    private String workerId;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
