# ReliableTask 发布流程

本文档用于维护 ReliableTask 的 GitHub Release。当前项目处于首次开源预览阶段，发布流程以人工校验和基础 CI 为主，不引入复杂自动发布流水线。

## 版本策略

- 版本号遵循 SemVer：`MAJOR.MINOR.PATCH`。
- `0.x` 阶段为预览期，API 仍可能调整；破坏性变化必须写入 `CHANGELOG.md`。
- Maven 开发版本使用 `-SNAPSHOT`，例如 `0.4.0-SNAPSHOT`。
- Git Tag 使用 `vX.Y.Z`，例如 `v0.3.0`。

## 发布分支

- 日常开发合入 `main`。
- 首次开源版本使用 `main` 加 annotated tag 的轻量发布流程。
- 不为 `v0.1.0` 创建 release 分支；只有在发布修复需要隔离、长期维护多个版本线或多人并行冲突明显时，才考虑单独 release 分支。

## 发布前 Checklist

- [ ] 当前在 `main` 分支。
- [ ] 已执行 `git pull --ff-only origin main` 并确认本地是最新代码。
- [ ] `git status --short` 为空，没有待提交变更。
- [ ] `git status --ignored --short` 中没有待提交的 `.env`、`application.yml`、IDE 配置、`.m2`、`target` 或其他本地文件。
- [ ] 运行敏感信息扫描，确认没有真实密钥、Token、Cookie、真实账号、真实内部地址或真实用户数据。
- [ ] 确认示例配置只存在于 `.env.example` 和 `application-example.yml`，且只包含占位符。
- [ ] 更新 `CHANGELOG.md`，把目标版本日期改为实际发布日期。
- [ ] 准备 `docs/releases/vX.Y.Z.md` 作为 GitHub Release Notes。
- [ ] 将根 `pom.xml` 和各模块版本从 `X.Y.Z-SNAPSHOT` 调整为 `X.Y.Z`。
- [ ] 执行 `mvn -B test` 并确认通过。
- [ ] 至少执行一种真实 MySQL 集成测试：`mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test`，或在专用本地 MySQL 测试库上执行 `mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test`。
- [ ] 如果 Docker/Testcontainers 或本地 MySQL 环境不可用，必须在发布记录中单独写明阻塞证据和未验证范围，不能把跳过的集成测试写成通过。
- [ ] 更新或引用 `docs/review/RELIABLE_TASK_V03_V04_READINESS_REPORT.md`，确认 release notes 中的验证状态与 readiness 事实一致。
- [ ] 对 v0.5.x 及后续生产运维版本，更新或引用 `docs/review/RELIABLE_TASK_V05_READINESS_REPORT.md`，确认 Admin 运维查询、dead letter SPI、监控 runbook 和 MySQL 验证状态与 release notes 一致。
- [ ] 对 v0.6.x 及后续 SPI/模块边界版本，更新或引用 `docs/review/RELIABLE_TASK_V06_READINESS_REPORT.md`，确认 Store 窄接口、payload codec、interceptor chain、trace/name/metadata SPI、Worker/Admin starter 拆分、migration 脚本和验证状态与 release notes 一致。
- [ ] 确认 README 快速开始、Demo 文档和安全说明与当前版本一致。
- [ ] 确认 README、中文 README 和 Demo 文档同步说明 v0.5 Admin 运维查询、dead letter SPI、生产默认值和本地 Demo opt-in 差异。
- [ ] 确认 README、中文 README 和 Demo 文档同步说明 v0.6 Worker-only starter、Admin starter opt-in、schema.sql/Flyway/Liquibase 三选一初始化，以及 Store/payload/interceptor/trace/name/metadata 迁移路径。
- [ ] 确认 `docs/operations/reliable-task-monitoring.md`、`docs/operations/reliable-task-runbook.md` 和 `docs/operations/prometheus-alerts-example.yml` 与当前真实指标和 Admin API 一致。
- [ ] 确认生产接入 checklist 覆盖 Admin 内部网络隔离、认证授权、审计、payload 敏感信息、MySQL profile、告警阈值和恢复策略。
- [ ] 确认监控告警示例不包含真实内网地址、凭据、Token、生产阈值承诺或未实现指标。
- [ ] 确认 `SECURITY.md` 中的安全联系方式和 GitHub Security Advisory 链接，且没有内部地址。

## 测试分层

| 层级 | 命令 | 默认 CI | 说明 |
| --- | --- | --- | --- |
| 基础单元/自动配置/H2 schema | `mvn -B test` | 是，PR 和 push 自动执行 | 不依赖 Docker 或本地 MySQL，是最低合入门槛。 |
| Testcontainers MySQL 集成测试 | `mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | 否，可通过 GitHub Actions `workflow_dispatch` 手动开启 | 需要 Docker，用于验证真实 MySQL 唯一键、事务、并发 claim、租约 CAS 和恢复语义。 |
| 本地 MySQL 集成测试 | `mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test` | 否，仅本地或专用环境 | 需要设置 `RELIABLE_TASK_IT_JDBC_URL`、`RELIABLE_TASK_IT_USERNAME`、`RELIABLE_TASK_IT_PASSWORD`，只允许连接可丢弃的集成测试库。 |

基础 CI 必须保持轻量稳定，不应因为 Docker、Testcontainers 镜像拉取、本地 MySQL 或专用网络不可用而阻塞普通 PR。发布前验收需要补充至少一种真实 MySQL profile；若被环境阻塞，应按“已通过 / 未执行 / 阻塞原因 / 推荐恢复步骤”分层记录。

## 发布命令

```bash
git checkout main
git pull --ff-only origin main
git status
mvn -B test
mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test

git add CHANGELOG.md docs/releases/v0.1.0.md README.md .env.example SECURITY.md docs/release-process.md docs/open-source-check-report.md pom.xml reliable-task-*/pom.xml
git commit -m "docs(release): prepare v0.1.0"

git tag -a v0.1.0 -m "Release v0.1.0"
git push origin main
git push origin v0.1.0
```

使用整理好的 Release Notes 创建 GitHub Release：

```bash
gh release create v0.1.0 \
  --title "v0.1.0 - Initial Release" \
  --notes-file docs/releases/v0.1.0.md
```

如果想先创建草稿 Release：

```bash
gh release create v0.1.0 \
  --title "v0.1.0 - Initial Release" \
  --notes-file docs/releases/v0.1.0.md \
  --draft
```

如果发布后继续开发下一个版本：

```bash
mvn versions:set -DnewVersion=0.4.0-SNAPSHOT
git add pom.xml reliable-task-*/pom.xml
git commit -m "chore: start next development iteration"
git push origin main
```

## GitHub Release 内容模板

GitHub Release 内容以 `docs/releases/vX.Y.Z.md` 为准。Release Notes 应面向用户说明 Highlights、What's Included、Getting Started、Notes 和 Checks，不要简单复制 git log，也不要写未实现能力。

## 发布后验证 Checklist

- [ ] GitHub tag `v0.1.0` 存在。
- [ ] GitHub Release 页面标题、说明、Tag 和源码包正确。
- [ ] Release Notes 展示正常。
- [ ] 从 Tag 重新检出后可以执行 `mvn -B test`。
- [ ] 从 Tag 重新检出后至少一种真实 MySQL 集成测试 profile 通过，或发布记录明确说明环境阻塞和未验证范围。
- [ ] 源码包不包含 `.env`、本地 `application.yml`、IDE 配置、`.m2`、`target`。
- [ ] Issue 模板、PR 模板和 CI 在 GitHub 页面可用。
- [ ] README 中的快速开始、配置示例、License、Security 链接可访问。
