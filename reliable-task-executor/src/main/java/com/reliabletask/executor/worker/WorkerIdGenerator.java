package com.reliabletask.executor.worker;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Worker ID 生成器
 *
 * <p>生成全局唯一的 Worker 标识，格式: hostname:shortUUID
 * <p>示例: myhost-01:a3f8b2c1
 *
 * <p>使用单例模式，Worker ID 在应用生命周期内保持不变。
 */
@Slf4j
public class WorkerIdGenerator {

    private static final String WORKER_ID;

    static {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
            log.warn("Failed to get hostname, using 'unknown'", e);
        }

        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WORKER_ID = hostname + ":" + shortUuid;

        log.info("Worker ID generated: {}", WORKER_ID);
    }

    private WorkerIdGenerator() {
    }

    /**
     * 获取当前 Worker ID
     *
     * @return 全局唯一的 Worker 标识
     */
    public static String getWorkerId() {
        return WORKER_ID;
    }
}
