package com.reliabletask.core.spi.noop;

import com.reliabletask.core.spi.TaskAuthorizationProvider;

/**
 * 默认权限实现：兼容 auth 关闭场景，允许所有操作。
 *
 * <p>该实现只应在 auth.enabled=false 时使用。它不读取 operator/action/taskId，
 * 因此不能被误认为是“默认管理员”或“内置 RBAC”。
 */
public class NoopTaskAuthorizationProvider implements TaskAuthorizationProvider {

    @Override
    public boolean isAllowed(String operator, String action, Long taskId) {
        return true;
    }
}
