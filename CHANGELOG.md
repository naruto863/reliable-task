# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/) 的结构，并使用 [Semantic Versioning](https://semver.org/) 进行版本管理。

## [Unreleased]

### Added

- TODO: 记录下一个版本新增能力。

### Changed

- TODO: 记录下一个版本行为变化。

### Fixed

- TODO: 记录下一个版本缺陷修复。

### Security

- TODO: 记录安全相关修复或加固。

## [0.1.0] - 2026-05-04

### Added

- 首次开源 ReliableTask 预览版。
- 提供 `reliable-task-core`，包含任务领域模型、SPI、异常、枚举和内置重试策略。
- 提供 `reliable-task-store`，包含 MyBatis-Plus 存储实现和 MySQL schema。
- 提供 `reliable-task-executor`，包含 Worker 调度、任务执行、重试、补偿和线程池隔离能力。
- 提供 `reliable-task-admin`，包含任务查询、任务运维、统计和 Worker 查询 API。
- 提供 `reliable-task-spring-boot-starter`，支持 Spring Boot 自动装配。
- 提供 `reliable-task-demo`，展示事务内投递、对象 payload、重复投递和失败重试场景。
- 新增基础开源文件、Issue 模板、PR 模板和 GitHub Actions Maven 测试工作流。

### Security

- 移除 Demo 中已跟踪的真实样式数据库密码，改为 `application-example.yml` 和环境变量占位符。
- 首次开源分支不公开历史分析、提示词、运行日志等非业务材料。

### Known Limitations

- Maven Central 发布状态为 TODO，当前预览阶段以源码构建和本地安装为主。
- Admin API 生产使用前必须接入认证、授权、审计和网络访问控制。
- `0.x` 阶段 API 仍可能调整，破坏性变化会在本文件中记录。
