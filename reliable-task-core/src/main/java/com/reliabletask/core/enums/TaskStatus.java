package com.reliabletask.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 *
 * <p>V1.5 主生命周期状态流转图:
 * <pre>
 * PENDING(待执行)
 *    │
 *    └──► RUNNING(执行中) ──► SUCCESS(成功)
 *              │
 *              ├──► RETRYING(重试中) ──► RUNNING
 *              │
 *              ├──► DEAD(死亡/需人工干预) ──► PENDING(人工重新入队)
 *              │
 *              └──► CANCELLED(已取消) ──► PENDING(人工重新入队)
 * </pre>
 *
 * <p>FAILED 保留为兼容状态码和执行日志失败结果，不作为 V1.5 主任务生命周期的流转状态。
 *
 * <p>数据库存储为 TINYINT，与 code 字段严格对应。
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    /**
     * 待执行：任务已投递，等待 Worker 拉取执行
     */
    PENDING(0, "待执行"),

    /**
     * 执行中：Worker 已拉取并正在执行
     */
    RUNNING(1, "执行中"),

    /**
     * 成功：任务执行成功
     */
    SUCCESS(2, "成功"),

    /**
     * 失败：兼容状态码，V1.5 主任务生命周期不主动流转到该状态。
     * 任务执行失败后应直接进入 RETRYING 或 DEAD。
     */
    FAILED(3, "失败"),

    /**
     * 重试中：已计算下次重试时间，等待到达后重新执行
     */
    RETRYING(4, "重试中"),

    /**
     * 死亡：达到最大重试次数或不可重试异常，需人工干预
     */
    DEAD(5, "死亡/需人工干预"),

    /**
     * 已取消：任务被手动终止，不可再执行
     */
    CANCELLED(6, "已取消");

    /**
     * 状态码，与数据库存储值严格对应
     */
    private final int code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 根据 code 查找对应的状态枚举
     *
     * @param code 状态码
     * @return 对应的 TaskStatus 枚举
     * @throws IllegalArgumentException 如果 code 不合法
     */
    public static TaskStatus fromCode(int code) {
        for (TaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus code: " + code);
    }

    /**
     * 判断是否为系统自动流转终态。
     *
     * <p>终态包括：SUCCESS（成功）、CANCELLED（已取消）。
     * DEAD 和 CANCELLED 均可通过管理接口人工重新入队回到 PENDING；
     * 其中 CANCELLED 在自动执行视角仍视为终止态，避免被 Worker 再次调度。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == CANCELLED;
    }

    /**
     * 判断是否为可执行状态（可以被 Worker 拉取）
     *
     * <p>包括：PENDING（待执行）、RETRYING（重试中）
     */
    public boolean isExecutable() {
        return this == PENDING || this == RETRYING;
    }
}
