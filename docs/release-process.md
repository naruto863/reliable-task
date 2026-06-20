# ReliableTask 发布流程

本文档用于维护 ReliableTask 的 GitHub Release。`0.x` 仍按预览发布治理；`v1.0.0` 起增加 Maven Central 发布链路、release dry-run workflow 和更完整的 readiness 事实记录。

## 版本策略

- 版本号遵循 SemVer：`MAJOR.MINOR.PATCH`。
- `0.x` 阶段为预览期，API 仍可能调整；破坏性变化必须写入 `CHANGELOG.md`。
- Maven 开发版本使用 `-SNAPSHOT`，例如 `0.4.0-SNAPSHOT`。
- Git Tag 使用 `vX.Y.Z`，例如 `v0.3.0`。
- v1.0 起 Maven Central 发布使用 Central Publisher Portal。发布 workflow 只在手动 `workflow_dispatch` 下运行，默认 `dry-run`，真实发布必须显式选择 `publish` 并输入 `PUBLISH` 确认。

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
- [ ] 对 v0.7.x 及后续 Console preview 版本，更新或引用 `docs/review/RELIABLE_TASK_V07_READINESS_REPORT.md`，确认 Web 控制台、console-safe payload、写操作门禁、批量操作、demo/proxy 文档和 smoke 验证状态与 release notes 一致。
- [ ] 对 v1.0.x 及后续稳定开源版本，更新或引用 `docs/v1.0/RELIABLE_TASK_V10_READINESS_REPORT.md`、`docs/migration/v1.0-upgrade-guide.md` 和 `docs/releases/v1.0.0.md`，确认 API/SPI freeze、schema strategy、Maven Central profile、升级指南、测试矩阵和 release notes 一致。
- [ ] 确认 README 快速开始、Demo 文档和安全说明与当前版本一致。
- [ ] 确认 README、中文 README 和 Demo 文档同步说明 v0.5 Admin 运维查询、dead letter SPI、生产默认值和本地 Demo opt-in 差异。
- [ ] 确认 README、中文 README 和 Demo 文档同步说明 v0.6 Worker-only starter、Admin starter opt-in、schema.sql/Flyway/Liquibase 三选一初始化，以及 Store/payload/interceptor/trace/name/metadata 迁移路径。
- [ ] 确认 README、中文 README、Demo 文档和 `reliable-task-console/README.md` 同步说明 v0.7 Console preview、独立静态部署、反向代理、payload 安全和写操作前置条件。
- [ ] 确认 `docs/operations/reliable-task-monitoring.md`、`docs/operations/reliable-task-runbook.md` 和 `docs/operations/prometheus-alerts-example.yml` 与当前真实指标和 Admin API 一致。
- [ ] 确认生产接入 checklist 覆盖 Admin 内部网络隔离、认证授权、审计、payload 敏感信息、MySQL profile、告警阈值和恢复策略。
- [ ] 确认监控告警示例不包含真实内网地址、凭据、Token、生产阈值承诺或未实现指标。
- [ ] 确认 `SECURITY.md` 中的安全联系方式和 GitHub Security Advisory 链接，且没有内部地址。

## 测试分层

| 层级 | 命令 | 默认 CI | 说明 |
| --- | --- | --- | --- |
| 基础单元/自动配置/H2 schema | `mvn -B test` | 是，PR 和 push 自动执行 | 不依赖 Docker 或本地 MySQL，是最低合入门槛。 |
| Console 类型检查/单测/构建 | `cd reliable-task-console && npm ci && npm run typecheck && npm run lint && npm run test -- --run && npm run build` | 是，v0.7 后应自动执行 | 独立前端工程，不进入 Maven reactor。 |
| Console 只读 smoke | `cd reliable-task-console && npm run test:smoke` | 可选，建议手动或独立 job | 需要 Playwright browser；当前 smoke 使用 mock Admin API 覆盖 Dashboard、Tasks、Detail、Workers、Audit 只读路径。 |
| Testcontainers MySQL 集成测试 | `mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | 否，可通过 GitHub Actions `workflow_dispatch` 手动开启 | 需要 Docker，用于验证真实 MySQL 唯一键、事务、并发 claim、租约 CAS 和恢复语义。 |
| 本地 MySQL 集成测试 | `mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test` | 否，仅本地或专用环境 | 需要设置 `RELIABLE_TASK_IT_JDBC_URL`、`RELIABLE_TASK_IT_USERNAME`、`RELIABLE_TASK_IT_PASSWORD`，只允许连接可丢弃的集成测试库。 |
| Release profile dry-run | `mvn -B -Prelease -DskipTests verify` | 否，可通过 Release workflow 手动执行 | 生成 sources/javadoc jar 并验证 release profile；默认不解析 GPG 签名 profile、不上传 Central。 |
| 依赖安全/清单检查 | `mvn -B -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree` 和 `cd reliable-task-console && npm audit --audit-level=high --registry=https://registry.npmjs.org` | 否，通过 Release workflow 手动开启 | 用于发布前治理输入；发现项必须在 TASK-011 或 readiness 中分级处理。 |
| Maven Central publish | `mvn -B -Prelease,release-sign -DskipTests -Dgpg.skip=false -Dcentral.skipPublishing=false -Dcentral.autoPublish=true deploy` | 否，仅手动发布 | 需要 Central token 和 GPG 私钥。版本发布后不可覆盖，只能发布新版本修复。 |

基础 CI 必须保持轻量稳定，不应因为 Docker、Testcontainers 镜像拉取、本地 MySQL、Playwright browser 下载或专用网络不可用而阻塞普通 PR。发布前验收需要补充至少一种真实 MySQL profile；v0.7 Console 发布还应补充 console build 和 smoke。若被环境阻塞，应按“已通过 / 未执行 / 阻塞原因 / 推荐恢复步骤”分层记录。

v0.7 Console preview 的发布准备以 `docs/review/RELIABLE_TASK_V07_READINESS_REPORT.md` 为事实来源。若创建 `v0.7.0` tag 或 GitHub Release，Release Notes 中的验证状态必须与该报告一致，并且不能把未执行的 MySQL/Testcontainers 集成测试写成通过。

## v1.0 Maven Central dry-run 与发布工作流

### 本地 dry-run

无 Central/GPG 凭证时可以执行：

```bash
mvn -B -DskipTests package
mvn -B -Prelease -DskipTests package
mvn -B -Prelease -DskipTests verify
```

这些命令只验证 Maven 元数据、sources jar、javadoc jar 和 release profile，不上传 Maven Central，也不创建 tag 或 GitHub Release。

如需在本地验证签名配置，必须使用可发布的 GPG 私钥，并显式启用签名 profile：

```bash
mvn -B -Prelease,release-sign -DskipTests -Dgpg.skip=false verify
```

### GitHub Actions dry-run

`.github/workflows/release.yml` 通过 `workflow_dispatch` 手动触发，默认参数：

- `mode=dry-run`
- `version=1.0.0`
- `run-mysql-it=false`
- `run-console-smoke=false`
- `run-security-scan=false`

默认 dry-run 会执行：

```bash
mvn -B test
mvn -B -Prelease -DskipTests verify
```

如果勾选 `run-mysql-it`，workflow 会额外执行 Testcontainers MySQL profile。如果勾选 `run-console-smoke`，workflow 会额外执行 Console typecheck、lint、unit、build 和 Playwright smoke。

如果勾选 `run-security-scan`，workflow 会额外输出 Maven dependency tree 并运行 Console `npm audit --audit-level=high --registry=https://registry.npmjs.org`。该层用于发布治理输入，不是普通 PR 默认门禁；发现项必须在开源治理任务中分级处理。

### Maven Central secrets

真实发布前，仓库需要配置以下 GitHub Actions secrets：

- `CENTRAL_USERNAME`: Central Portal user token username。
- `CENTRAL_PASSWORD`: Central Portal user token password。
- `GPG_PRIVATE_KEY`: ASCII-armored GPG private key。
- `GPG_PASSPHRASE`: GPG private key passphrase。

不要把 Central token、GPG 私钥或 passphrase 写入仓库文件、workflow 明文、README 或 release notes。

### Maven Central publish

真实发布必须满足：

1. 用户明确授权发布当前版本。
2. `docs/v1.0/RELIABLE_TASK_V10_READINESS_REPORT.md` 和 `docs/releases/v1.0.0.md` 已准备好。
3. Release workflow 选择 `mode=publish`。
4. `confirm-publish` 输入严格等于 `PUBLISH`。
5. Central/GPG secrets 已配置。

workflow 发布命令为：

```bash
mvn -B -Prelease,release-sign -DskipTests -Dgpg.skip=false -Dcentral.skipPublishing=false -Dcentral.autoPublish=true deploy
```

该命令会签名 artifacts 并通过 Central Publisher Portal 发布。不要在未授权或未确认版本号时运行。

### 失败处理和回滚

- dry-run 失败：修复代码、POM、Javadoc 或环境问题后重新运行；没有远端发布状态需要回滚。
- MySQL 或 Console 可选验证失败：按 `PASS`、`FAIL_CODE`、`BLOCKED_ENV`、`NOT_RUN` 分层记录，不能写成通过。
- Central 上传前失败：修复凭证、GPG、POM 或网络问题后重新运行。
- Central 已上传但未完成发布：优先在 Central Portal 中丢弃 deployment，修复后重新上传。
- Central 已发布：版本不可覆盖，不能删除后重发同版本；如需修复，发布新的 patch 版本，并在 release notes 中说明。

### 发布后验证

Maven Central 发布后必须验证：

- Central Portal 中 deployment 状态为 published。
- Maven Central 搜索页或仓库路径能看到 `com.reliabletask` 目标版本。
- 使用临时 consumer 项目依赖 `reliable-task-spring-boot-starter` 和 `reliable-task-admin-spring-boot-starter` 可以解析并启动最小 Spring Boot context。
- README、中文 README、`docs/releases/v1.0.0.md` 和 GitHub Release 中的安装命令与实际 Central 状态一致。
- Git tag 和 GitHub Release 只在 Central 发布事实明确后创建或更新。

## 发布命令

以下命令保留为早期 GitHub-only preview 发布示例。`v1.0.0` Maven Central 发布应优先使用上文的 dry-run 和 release workflow，并且只有在 readiness、Central/GPG secrets 和用户授权齐备后执行。

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
