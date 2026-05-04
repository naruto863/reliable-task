package com.reliabletask.core.spi;

/**
 * Admin 操作权限 SPI。
 */
public interface TaskAuthorizationProvider {

    /**
     * 判断指定操作是否允许执行。
     *
     * @param operator 操作人
     * @param action   权限动作
     * @param taskId   任务 ID，非任务级操作可为空
     * @return true 表示允许
     */
    boolean isAllowed(String operator, String action, Long taskId);
}
