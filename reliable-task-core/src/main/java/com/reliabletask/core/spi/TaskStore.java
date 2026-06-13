package com.reliabletask.core.spi;

/**
 * 存储层兼容门面。
 *
 * <p>v0.6 将原先偏宽的存储 SPI 拆分为命令、查询和运维三类窄接口。
 * 旧的 {@code TaskStore} 保留为兼容门面，继续继承全部能力，避免已经实现
 * {@code TaskStore} 的用户在 v0.x 阶段被迫立即迁移。
 *
 * <p>新代码应优先依赖最小所需接口：
 * <ul>
 *   <li>{@link TaskCommandStore}: 投递、Worker 调度、状态回写、恢复和执行日志。</li>
 *   <li>{@link TaskQueryStore}: Admin 只读查询、统计和运维分析视图。</li>
 *   <li>{@link TaskOperationsStore}: 人工操作、审计、批量操作和 Worker 心跳。</li>
 * </ul>
 */
public interface TaskStore extends TaskCommandStore, TaskQueryStore, TaskOperationsStore {
}
