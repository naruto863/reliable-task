# ReliableTask 发布流程

本文档用于维护 ReliableTask 的 GitHub Release。当前项目处于首次开源预览阶段，发布流程以人工校验和基础 CI 为主，不引入复杂自动发布流水线。

## 版本策略

- 版本号遵循 SemVer：`MAJOR.MINOR.PATCH`。
- `0.x` 阶段为预览期，API 仍可能调整；破坏性变化必须写入 `CHANGELOG.md`。
- Maven 开发版本使用 `-SNAPSHOT`，例如 `0.2.0-SNAPSHOT`。
- Git Tag 使用 `vX.Y.Z`，例如 `v0.1.0`。

## 发布分支

- 日常开发合入 `main`。
- 发布准备使用 `release/vX.Y.Z` 分支，例如 `release/v0.1.0`。
- 发布分支只接受版本号、文档、测试修复和开源安全修正。

## 发布前 Checklist

- [ ] 确认 `git status --ignored --short` 中没有待提交的 `.env`、`application.yml`、IDE 配置、`.m2`、`target` 或其他本地文件。
- [ ] 运行敏感信息扫描，确认没有真实密钥、Token、Cookie、真实账号、真实内部地址或真实用户数据。
- [ ] 确认示例配置只存在于 `.env.example` 和 `application-example.yml`。
- [ ] 更新 `CHANGELOG.md`，把目标版本日期改为实际发布日期。
- [ ] 将根 `pom.xml` 和各模块版本从 `X.Y.Z-SNAPSHOT` 调整为 `X.Y.Z`。
- [ ] 执行 `mvn -B test` 并确认通过。
- [ ] 确认 README 快速开始、Demo 文档和安全说明与当前版本一致。
- [ ] 确认 `SECURITY.md` 中的安全联系方式和 GitHub Security Advisory 链接；未知时保留 `TODO`，不要写内部地址。

## 发布命令

```bash
mvn versions:set -DnewVersion=0.1.0
mvn -B test
git add pom.xml reliable-task-*/pom.xml CHANGELOG.md
git commit -m "chore: prepare release v0.1.0"
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin main
git push origin v0.1.0
```

如果发布后继续开发下一个版本：

```bash
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
git add pom.xml reliable-task-*/pom.xml
git commit -m "chore: start next development iteration"
git push origin main
```

## GitHub Release 内容模板

```markdown
# ReliableTask v0.1.0 - First Open Source Preview

ReliableTask 是一个基于 Spring Boot 3 的可靠异步任务执行框架，面向业务事务提交后需要稳定执行异步动作的场景。

## Highlights

- 首次开源 `reliable-task-core`、`reliable-task-store`、`reliable-task-executor`、`reliable-task-admin`、`reliable-task-spring-boot-starter` 和 `reliable-task-demo`。
- 支持事务内任务投递、数据库任务存储、Worker 调度、自动重试、超时补偿、线程池隔离和管理 API。
- 提供 Apache-2.0 License、README、CHANGELOG、CONTRIBUTING、SECURITY、Issue/PR 模板和基础 Maven CI。

## Requirements

- Java 21+
- Maven 3.8+
- Spring Boot 3.2.5
- MySQL 8.0+

## Security Notes

- Admin API 生产使用前必须接入认证、授权、审计和网络访问控制。
- 不要提交或公开真实数据库账号、密码、Token、Cookie、内部地址或真实用户数据。
- Maven Central 发布状态：TODO。

## Changelog

详见 `CHANGELOG.md`。
```

## 发布后验证 Checklist

- [ ] GitHub Release 页面标题、说明、Tag 和源码包正确。
- [ ] 从 Tag 重新检出后可以执行 `mvn -B test`。
- [ ] 源码包不包含 `.env`、本地 `application.yml`、IDE 配置、`.m2`、`target`。
- [ ] Issue 模板、PR 模板和 CI 在 GitHub 页面可用。
- [ ] README 中的快速开始、配置示例、License、Security 链接可访问。

