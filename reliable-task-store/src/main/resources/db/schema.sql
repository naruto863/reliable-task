-- ============================================================
-- ReliableTask 异步任务队列组件 - 数据库建表脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- 引擎:   InnoDB
-- ============================================================

-- ----------------------------
-- 1. 任务主表 reliable_task
-- ----------------------------
-- 职责: 存储异步任务实例的完整信息，包括任务参数、状态、重试配置、执行结果等
-- 并发安全: 通过 uk_biz_unique_key 唯一索引保证投递幂等，通过 FOR UPDATE SKIP LOCKED 保证消费不重复
CREATE TABLE IF NOT EXISTS `reliable_task`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_type`         VARCHAR(64)  NOT NULL COMMENT '任务类型（如 CREATE_SHIPMENT）',
    `biz_type`          VARCHAR(64)  NOT NULL COMMENT '业务类型（如 ORDER/USER/PAYMENT）',
    `biz_id`            VARCHAR(128) NOT NULL COMMENT '业务唯一标识（如订单号）',
    `biz_unique_key`    VARCHAR(256) NOT NULL COMMENT '幂等键，格式: task_type:biz_type:biz_id，保证同一业务动作不重复投递',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '任务状态: 0-PENDING(待执行) 1-RUNNING(执行中) 2-SUCCESS(成功) 3-FAILED(失败) 4-RETRYING(重试中) 5-DEAD(死亡/需人工干预) 6-CANCELLED(已取消)',
    `priority`          TINYINT      NOT NULL DEFAULT 5 COMMENT '优先级，0-9，数字越小优先级越高，默认 5',
    `payload`           TEXT         NOT NULL COMMENT '任务参数，JSON 格式',
    `execute_count`     INT          NOT NULL DEFAULT 0 COMMENT '已执行次数（含首次执行和重试）',
    `version`           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，用于并发抢占和状态更新',
    `max_retry_count`   INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数，默认 3',
    `retry_strategy`    VARCHAR(32)  NOT NULL DEFAULT 'EXPONENTIAL' COMMENT '重试策略: FIXED(固定间隔) EXPONENTIAL(指数退避)',
    `retry_interval_ms` BIGINT       NOT NULL DEFAULT 1000 COMMENT '基础重试间隔，单位毫秒，默认 1000ms',
    `next_execute_time` DATETIME     NOT NULL COMMENT '下次执行时间，用于延迟任务和重试调度',
    `shard_key`         VARCHAR(128)          DEFAULT NULL COMMENT '分片键，用于任务分片路由（可选）',
    `tenant_id`         VARCHAR(64)           DEFAULT NULL COMMENT '租户标识，用于多租户隔离（可选）',
    `worker_id`         VARCHAR(64)           DEFAULT NULL COMMENT '当前执行节点 ID，用于宕机恢复和追踪',
    `locked_at`         DATETIME              DEFAULT NULL COMMENT '任务被 Worker 抢占的时间',
    `lock_expire_at`    DATETIME              DEFAULT NULL COMMENT '任务锁过期时间，用于识别超时 RUNNING 任务',
    `heartbeat_time`    DATETIME              DEFAULT NULL COMMENT 'Worker 执行任务期间最近一次心跳时间',
    `last_execute_time` DATETIME              DEFAULT NULL COMMENT '最近一次开始执行时间',
    `error_msg`         TEXT                  DEFAULT NULL COMMENT '最近一次执行的异常信息，便于快速排查',
    `last_error_code`   VARCHAR(128)          DEFAULT NULL COMMENT '最近一次执行失败的错误码或异常类型',
    `trace_id`          VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪 ID，用于全链路日志追踪',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `finish_time`       DATETIME              DEFAULT NULL COMMENT '任务完成时间（成功/失败/取消）',
    PRIMARY KEY (`id`),
    -- 幂等控制：防止同一业务动作重复投递
    UNIQUE KEY `uk_biz_unique_key` (`biz_unique_key`),
    -- Worker 拉取任务的核心索引：按状态 + 执行时间排序
    KEY `idx_status_next_priority_id` (`status`, `next_execute_time`, `priority`, `id`),
    -- 恢复扫描索引：快速定位锁已过期的 RUNNING 任务
    KEY `idx_lock_expire` (`status`, `lock_expire_at`),
    -- 按任务类型查询
    KEY `idx_task_type` (`task_type`),
    -- 按业务维度查询
    KEY `idx_biz_type_biz_id` (`biz_type`, `biz_id`),
    -- 多租户隔离查询
    KEY `idx_tenant_id` (`tenant_id`),
    -- 按创建时间范围查询/归档
    KEY `idx_create_time` (`create_time`),
    -- Admin 终态统计：按状态和完成时间统计今日成功/失败/死信
    KEY `idx_status_finish_time` (`status`, `finish_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='异步任务主表';


-- ----------------------------
-- 2. 任务执行日志表 reliable_task_log
-- ----------------------------
-- 职责: 记录任务每次执行的详细结果，包括执行时间、耗时、成功/失败状态、异常信息
-- 设计说明: 与主表分离，避免主表因历史执行记录膨胀，影响拉取和更新性能
CREATE TABLE IF NOT EXISTS `reliable_task_log`
(
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`       BIGINT   NOT NULL COMMENT '关联的任务主表 ID',
    `attempt_no`    INT               DEFAULT NULL COMMENT '本次执行序号',
    `status_before` VARCHAR(32)       DEFAULT NULL COMMENT '执行前任务状态',
    `status_after`  VARCHAR(32)       DEFAULT NULL COMMENT '执行后任务状态',
    `execute_time`  DATETIME NOT NULL COMMENT '执行开始时间',
    `duration_ms`   BIGINT            DEFAULT NULL COMMENT '执行耗时，单位毫秒',
    `status`        TINYINT  NOT NULL COMMENT '执行结果: 2-SUCCESS(成功) 3-FAILED(失败)',
    `error_code`    VARCHAR(128)      DEFAULT NULL COMMENT '错误码或异常类型',
    `error_msg`     TEXT              DEFAULT NULL COMMENT '异常堆栈信息',
    `worker_id`     VARCHAR(64)       DEFAULT NULL COMMENT '执行节点 ID',
    `trace_id`      VARCHAR(64)       DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    -- 按任务 ID 查询执行历史
    KEY `idx_task_id` (`task_id`),
    -- 按时间范围查询日志
    KEY `idx_task_log_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务执行日志表';


-- ----------------------------
-- 3. 任务重试记录表 reliable_task_retry
-- ----------------------------
-- 职责: 记录每次重试的计划和实际执行情况，用于分析重试行为和排查重试问题
-- 设计说明: MVP 阶段可选，但建议保留以便后续分析重试模式和优化重试策略
CREATE TABLE IF NOT EXISTS `reliable_task_retry`
(
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`         BIGINT   NOT NULL COMMENT '关联的任务主表 ID',
    `retry_count`     INT      NOT NULL COMMENT '第几次重试（从 1 开始）',
    `retry_strategy`  VARCHAR(32) NOT NULL COMMENT '本次重试使用的策略: FIXED/EXPONENTIAL',
    `planned_time`    DATETIME NOT NULL COMMENT '计划重试时间（根据策略计算得出）',
    `actual_time`     DATETIME          DEFAULT NULL COMMENT '实际执行重试时间',
    `result`          TINYINT           DEFAULT NULL COMMENT '重试结果: 0-待执行 2-成功 3-失败',
    `error_msg`       TEXT              DEFAULT NULL COMMENT '重试失败的异常信息',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    -- 按任务 ID 查询重试历史
    KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务重试记录表';


-- ----------------------------
-- 4. Worker 心跳表 reliable_task_worker
-- ----------------------------
-- 职责: 记录每个 Worker 实例的存活状态、执行容量和最近心跳，用于 Admin 运维查询和失联识别
-- 设计说明: worker_id 由执行端生成并保持唯一；心跳失败不影响任务主流程
CREATE TABLE IF NOT EXISTS `reliable_task_worker`
(
    `worker_id`              VARCHAR(128) NOT NULL COMMENT 'Worker 唯一标识',
    `app_name`               VARCHAR(128)          DEFAULT NULL COMMENT '应用名称或服务名',
    `host_name`              VARCHAR(128)          DEFAULT NULL COMMENT '主机名',
    `ip_address`             VARCHAR(64)           DEFAULT NULL COMMENT 'Worker IP 地址',
    `process_id`             VARCHAR(64)           DEFAULT NULL COMMENT '进程 ID',
    `status`                 TINYINT      NOT NULL DEFAULT 1 COMMENT 'Worker 状态: 0-OFFLINE 1-ONLINE 2-STALE',
    `running_task_count`     INT          NOT NULL DEFAULT 0 COMMENT '当前正在执行的任务数',
    `max_concurrency`        INT          NOT NULL DEFAULT 0 COMMENT 'Worker 最大并发数',
    `available_capacity`     INT          NOT NULL DEFAULT 0 COMMENT '当前可用执行容量',
    `last_heartbeat_time`    DATETIME     NOT NULL COMMENT '最近一次心跳时间',
    `start_time`             DATETIME              DEFAULT NULL COMMENT 'Worker 启动时间',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`worker_id`),
    -- Worker 运维查询：快速定位在线/失联 Worker
    KEY `idx_worker_heartbeat` (`status`, `last_heartbeat_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务 Worker 心跳表';


-- ----------------------------
-- 5. Admin 操作审计表 reliable_task_audit_log
-- ----------------------------
-- 职责: 记录人工管理操作和系统关键状态变更，便于追责、排障和回放操作历史
-- 设计说明: 审计写入失败不能影响原始管理操作结果；请求参数只保存摘要，避免大字段和敏感数据膨胀
CREATE TABLE IF NOT EXISTS `reliable_task_audit_log`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operation_type`     VARCHAR(64)  NOT NULL COMMENT '操作类型，如 TASK_RETRY/TASK_CANCEL/TASK_UPDATE_PAYLOAD/BATCH_REQUEUE',
    `operator`           VARCHAR(128)          DEFAULT NULL COMMENT '操作人，MVP 可来自请求头或系统默认值',
    `target_type`        VARCHAR(32)  NOT NULL COMMENT '操作目标类型: TASK/BATCH/SYSTEM',
    `target_id`          VARCHAR(128) NOT NULL COMMENT '操作目标 ID，任务 ID 或批量操作 ID',
    `task_id`            BIGINT                DEFAULT NULL COMMENT '关联任务 ID，非任务级操作可为空',
    `batch_operation_id` BIGINT                DEFAULT NULL COMMENT '关联批量操作 ID，非批量操作可为空',
    `request_summary`    TEXT                  DEFAULT NULL COMMENT '请求参数摘要，避免保存完整敏感 payload',
    `result`             VARCHAR(32)  NOT NULL COMMENT '操作结果: SUCCESS/FAILED/DENIED',
    `error_msg`          TEXT                  DEFAULT NULL COMMENT '操作失败原因',
    `trace_id`           VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 按操作目标查询审计历史
    KEY `idx_audit_target` (`target_type`, `target_id`),
    -- 按时间范围查询审计日志
    KEY `idx_audit_create_time` (`create_time`),
    -- 按任务查询审计历史
    KEY `idx_audit_task_id` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务管理操作审计表';


-- ----------------------------
-- 6. 批量运维操作表 reliable_task_batch_operation
-- ----------------------------
-- 职责: 记录每次批量重新入队、批量取消或 dry-run 预览的条件、状态和结果摘要
-- 设计说明: MVP 阶段批量操作可同步执行有限数量任务，但必须落库以便审计和恢复判断
CREATE TABLE IF NOT EXISTS `reliable_task_batch_operation`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operation_type`    VARCHAR(64)  NOT NULL COMMENT '批量操作类型: REQUEUE_DEAD/CANCEL_PENDING/PREVIEW',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '批量操作状态: PENDING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED',
    `operator`          VARCHAR(128)          DEFAULT NULL COMMENT '操作人，MVP 可来自请求头或系统默认值',
    `task_type`         VARCHAR(64)           DEFAULT NULL COMMENT '任务类型筛选条件',
    `task_status`       TINYINT               DEFAULT NULL COMMENT '任务状态筛选条件',
    `create_time_start` DATETIME              DEFAULT NULL COMMENT '任务创建时间筛选开始',
    `create_time_end`   DATETIME              DEFAULT NULL COMMENT '任务创建时间筛选结束',
    `operation_limit`   INT          NOT NULL DEFAULT 100 COMMENT '本次批量操作最大处理数量',
    `dry_run`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否仅预览: 0-否 1-是',
    `request_condition` TEXT                  DEFAULT NULL COMMENT '完整筛选条件摘要，JSON 格式',
    `total_count`       INT          NOT NULL DEFAULT 0 COMMENT '匹配任务总数或预览数量',
    `success_count`     INT          NOT NULL DEFAULT 0 COMMENT '成功处理数量',
    `fail_count`        INT          NOT NULL DEFAULT 0 COMMENT '失败处理数量',
    `failed_summary`    TEXT                  DEFAULT NULL COMMENT '失败任务 ID 或失败原因摘要',
    `error_msg`         TEXT                  DEFAULT NULL COMMENT '批量操作整体失败原因',
    `trace_id`          VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `finish_time`       DATETIME              DEFAULT NULL COMMENT '批量操作完成时间',
    PRIMARY KEY (`id`),
    -- 批量操作列表查询：按状态和创建时间排序
    KEY `idx_batch_status_create_time` (`status`, `create_time`),
    -- 按操作人查询批量操作历史
    KEY `idx_batch_operator_create_time` (`operator`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务批量运维操作表';
