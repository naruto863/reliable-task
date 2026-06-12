# Contributing Guide

感谢你关注 ReliableTask。这个项目处于首次开源预览阶段，当前优先级是安全、正确性和可维护性。

## 开发环境

- JDK 21+
- Maven 3.8+
- MySQL 8.0+，仅运行 Demo 时需要

本地验证：

```bash
mvn -B test
```

## 分支管理

- `main`：稳定主分支，应保持可构建、可测试。
- `feat/*`：新功能分支，例如 `feat/custom-retry-policy`。
- `fix/*`：缺陷修复分支，例如 `fix/retry-state-transition`。
- `docs/*`：文档分支，例如 `docs/release-guide`。
- `release/vX.Y.Z`：发布准备分支，例如 `release/v0.1.0`。

不建议长期保留大分支。小步提交、尽早发 PR 更容易审查。

## Commit Message

使用 Conventional Commits：

```text
feat: add custom retry strategy spi
fix: prevent duplicate task claim
docs: refresh demo quick start
test: cover retry timeout transition
ci: add maven test workflow
chore: align project version
```

常用类型：

- `feat`：新能力
- `fix`：缺陷修复
- `docs`：文档
- `test`：测试
- `ci`：CI 配置
- `refactor`：不改变行为的重构
- `chore`：构建、版本、仓库维护

## Pull Request 规范

PR 应包含：

- 变更动机和问题背景。
- 主要改动范围。
- 测试命令和结果。
- 兼容性影响。
- 安全影响，尤其是 Admin API、配置、凭据、日志、payload 处理。

PR 合并前应满足：

- 不提交真实密钥、真实账号、真实内部地址、Cookie、Token。
- 不提交本地 `application.yml`、`.env`、IDE 私有配置、构建产物。
- 新增或修改行为时补充对应测试，或说明无法自动化测试的原因。
- `mvn -B test` 通过，或明确记录本地环境阻塞并依赖 CI 验证。

## Issue 规范

Bug Report 应包含：

- 版本、JDK、Spring Boot、数据库版本。
- 复现步骤。
- 期望行为和实际行为。
- 相关日志、堆栈、配置片段，注意脱敏。

Feature Request 应包含：

- 业务场景。
- 期望能力。
- 可接受的兼容性和性能成本。
- 是否愿意提交 PR。

## 版本号规范

项目使用 SemVer：

- `MAJOR`：不兼容 API 或行为变化。
- `MINOR`：向后兼容的新能力。
- `PATCH`：向后兼容的缺陷修复。

`0.x` 阶段属于预览期，API 可能调整，但所有破坏性变化必须写入 `CHANGELOG.md`。

## Tag 与 Release

- Tag 使用 `vX.Y.Z`，例如 `v0.1.0`。
- 正式发布 tag 前，POM 版本必须去掉 `-SNAPSHOT`。
- GitHub Release 内容应来自 `CHANGELOG.md`，并包含兼容性说明、升级说明和已知限制。
- 初始阶段不使用复杂自动发布流水线。

发布准备示例：

```bash
mvn versions:set -DnewVersion=0.1.0
mvn -B test
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

发布后回到下一个开发版本：

```bash
mvn versions:set -DnewVersion=0.4.0-SNAPSHOT
```

## 文档和示例配置

- 文档使用中文优先。
- 示例配置只写入 `.env.example` 或 `application-example.yml`。
- 不要在 README、Issue、PR、测试日志中粘贴真实凭据。
