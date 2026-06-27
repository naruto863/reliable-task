package com.reliabletask.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ReliableTask Demo 启动类。
 *
 * <p>该模块用于本地演示 starter 自动装配、任务投递、重试、Admin SPI 和对象 payload；
 * 不承载生产业务逻辑。
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
