package com.reliabletask.core.spi.noop;

import com.reliabletask.core.spi.TaskAuthorizationProvider;

/**
 * 默认权限实现：兼容 auth 关闭场景，允许所有操作。
 */
public class NoopTaskAuthorizationProvider implements TaskAuthorizationProvider {

    @Override
    public boolean isAllowed(String operator, String action, Long taskId) {
        return true;
    }
}
