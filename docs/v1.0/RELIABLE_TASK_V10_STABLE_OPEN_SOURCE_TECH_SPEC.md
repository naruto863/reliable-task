# ReliableTask v1.0 稳定开源版技术规格

- version_scope: `v1.0.0`
- source_roadmap: `docs/review/RELIABLE_TASK_ROADMAP_V05_PLUS.md`
- baseline_version: `0.7.0`
- generated_for_protocol: `plans/codex_app/reliable-task/v1.0`
- generated_at: 2026-06-14 +08:00

## 1. 目标

v1.0.0 的目标是把 ReliableTask 从预览版本线推进到稳定开源版本线。它不再以新增大功能为主，而是冻结已经在 v0.5、v0.6、v0.7 中验证过的核心能力，补齐发布、升级、测试和文档治理，使外部用户可以从 Maven Central 直接接入真实 Spring Boot 项目。

本版本优先完成：

- 冻结核心 API/SPI、配置键、数据库升级策略和可靠性语义。
- 输出 v0.x 到 v1.0 的升级指南，明确破坏性变更、兼容适配层、schema 迁移和配置变更。
- 建立 Maven Central 发布链路，包括 sources jar、javadoc jar、GPG 签名、POM license/scm/developer 元数据和 release dry-run。
- 建立 v1.0 发布测试矩阵，覆盖默认单测、真实 MySQL、migration smoke、并发 claim、租约 CAS、recovery 竞态、幂等投递、Admin 权限和批量操作。
- 补齐示例矩阵、release notes、CHANGELOG、依赖安全扫描、CodeQL 或等价静态检查。

## 2. 非目标

v1.0.0 明确不做：

- 不承诺 exactly-once。ReliableTask 继续提供 at-least-once 调度语义，业务 Handler 必须承担外部副作用幂等责任。
- 不把 Web 控制台作为核心库强依赖。`reliable-task-console` 仍是独立 preview 前端，不进入 Maven reactor，也不阻塞 Worker-only starter 发布。
- 不承诺 PostgreSQL、MongoDB、Redis、Kafka、RabbitMQ、RocketMQ、XXL-Job 或其他官方后端实现。
- 不引入 Redis/Redisson `TaskLockStrategy` 官方实现。
- 不实现 DAG、任务依赖、工作流编排、人工审批流或复杂 Saga 引擎。
- 不实现复杂多租户权限平台、组织角色菜单体系或跨应用统一控制台。
- 不删除 v0.x 已公开的兼容门面，例如 `TaskStore` 和 deprecated `TaskSerializer`，除非升级指南明确且有替代路径。本版本默认不删除。

## 3. 当前基线

当前仓库版本为 `0.7.0`，根 POM 仍未配置 Maven Central 发布所需的完整元数据、sources/javadoc/signing/deploy 插件链路。

已具备的基础能力：

- v0.3/v0.4 可靠性基线：Worker 租约 CAS、状态回写保护、恢复扫描竞态保护、线程池拒绝和执行异常闭环。
- v0.5 生产运维能力：Admin 查询治理、最近失败、慢任务、失败 Top、timeline、dead-letter SPI、监控模板、runbook 和生产 checklist。
- v0.6 SPI 与模块边界：`TaskCommandStore`、`TaskQueryStore`、`TaskOperationsStore`，`TaskStore` 兼容门面，`TaskPayloadCodec`，`TaskInterceptor`，trace/name/metadata SPI，Worker-only starter 和 Admin starter 拆分，Flyway/Liquibase 初始脚本。
- v0.7 Console preview：独立 Vue/Vite 控制台、console-safe payload、Admin 写操作门禁、只读排障流、受控写操作 UI 和前端 CI 分层。
- 发布流程和文档：`CHANGELOG.md`、`docs/release-process.md`、`docs/releases/v0.7.0.md`、README、中文 README、demo README、operations docs。

v1.0 前仍需收口：

- API/SPI 稳定范围还没有形成可审阅 inventory。
- deprecated 接口、兼容门面和破坏性变更策略还没有 v1.x 级别承诺。
- schema 初始化已有 3 条路径，但 v0.x 到 v1.0 的升级顺序、回滚建议和 production 注意事项需要文档化。
- Maven Central 发布链路缺失。
- CI 仍偏预览版本线，缺少发布 dry-run、安全扫描和 v1.0 readiness 事实来源。

## 4. 稳定 API/SPI 范围

v1.0 应冻结以下面向用户或扩展实现者的能力。冻结含义是：v1.x 内不做源码级破坏性变更；如必须新增能力，优先通过 default 方法、新类型、可选配置或兼容适配器完成。

### 4.1 核心提交与处理 API

稳定范围：

- `TaskTemplate`
- `TaskSubmitRequest`
- `TaskSubmitResult`
- `TaskHandler`
- `@TaskHandler`
- `@TaskRetryable`
- `RetryStrategyType`
- `TaskStatus`
- `TaskStateMachine`

要求：

- `TaskTemplate` 的提交语义必须继续绑定本地事务和 Outbox 模型说明。
- `TaskSubmitRequest.idempotencyKey` 保持显式幂等键语义，长度、trim、敏感数据约束要写入升级指南和 README。
- `TaskHandler` 执行端继续承担业务幂等责任，文档不得暗示 exactly-once。
- `TaskStateMachine` 作为合法状态流转的唯一事实入口，新增状态或终态必须进入 v1.x 兼容评审。

### 4.2 事件、失败分类和诊断 API

稳定范围：

- `TaskEvent`
- `TaskEventType`
- `TaskEventListener`
- `FailureClassifier`
- `FailureDecision`
- `TaskExceptionFormatter`
- `TaskFailureDiagnostic`
- `TaskDeadLetterHandler`
- `DeadLetterContext`

要求：

- listener、classifier、formatter、dead-letter handler 失败必须隔离，不能破坏任务最终状态。
- 事件模型是轻量生命周期事件，不承诺完整事件溯源。
- 失败诊断不得鼓励记录 payload 明文、凭证、Token、个人身份标识或生产敏感数据。

### 4.3 Store 与 payload 扩展 API

稳定范围：

- `TaskCommandStore`
- `TaskQueryStore`
- `TaskOperationsStore`
- `TaskStore`
- `TaskExecutionLease`
- `TaskPayloadSerializer`
- `TaskPayloadCodec`
- `TaskPayloadCodecContext`
- `TaskSerializer`

兼容策略：

- 新实现优先依赖窄 Store 接口。
- `TaskStore` 在 v1.0 保留为兼容门面，继续继承窄接口，避免 v0.x 自定义实现被迫立即迁移。
- `TaskSerializer` 在 v1.0 继续保留 deprecated 兼容，不在 v1.0 删除；新接入应使用 `TaskPayloadSerializer` 或 `TaskPayloadCodec`。
- `TaskPayloadCodecContext` 当前不要求新增落库字段；payload schema version、压缩、加密和脱敏仍是后续可演进方向。

### 4.4 Starter 与配置键

稳定范围：

- `reliable-task-spring-boot-starter` 作为 Worker-only starter。
- `reliable-task-admin-spring-boot-starter` 作为 Admin/Web opt-in starter。
- `ReliableTaskProperties` 中现有 worker、recovery、retry、store、serializer、metrics、admin、admin.query、admin.console 等配置键。
- `additional-spring-configuration-metadata.json` 中公开配置说明。

要求：

- Admin REST API 继续默认关闭：`reliable-task.admin.enabled=false`。
- Admin 写接口继续默认关闭：`reliable-task.admin.write-enabled=false`。
- Admin 权限检查在 Admin 显式启用后默认开启：`reliable-task.admin.auth.enabled=true`。
- 写操作开启后仍必须满足权限、审计和确认 header 门禁。
- 配置键改名、默认值变化或语义变化必须进入升级指南和 release notes。

## 5. 数据库升级策略

v1.0 保留 3 种初始化路径：

- `db/schema.sql`
- Flyway `db/migration/V1__init_reliable_task_schema.sql`
- Liquibase `db/changelog/db.changelog-master.yaml`

约束：

- 同一个数据库只能选择一种初始化路径，不允许同时执行 `schema.sql`、Flyway 和 Liquibase。
- v1.0 必须文档化 v0.x 到 v1.0 的 schema 差异、推荐迁移顺序、备份建议和回滚边界。
- migration smoke 继续覆盖 H2 MySQL mode；真实 MySQL 发布验证至少执行 Testcontainers MySQL 或专用本地 MySQL profile 之一。
- 如果 v1.0 不新增 schema 字段，也必须在升级指南中明确“无需 schema 变更”的结论和验证证据。

## 6. Maven Central 发布设计

### 6.1 发布 artifact

v1.0 Maven Central 发布范围：

- `reliable-task-core`
- `reliable-task-store`
- `reliable-task-executor`
- `reliable-task-admin`
- `reliable-task-spring-boot-starter`
- `reliable-task-admin-spring-boot-starter`

不作为核心 Central artifact 发布：

- `reliable-task-demo`：本地演示应用，发布时应跳过 deploy，或明确仅作为源码示例参与 reactor 验证。
- `reliable-task-console`：独立前端 preview，不进入 Maven reactor，不阻塞 Java artifact 发布。可在 GitHub Release notes 中说明静态构建方式。
- `reliable-task-root`：聚合 POM 可按 Maven Central 要求发布或仅作为 parent POM 发布，具体以 Maven Central dry-run 结果为准。

### 6.2 POM 元数据

根 POM 或发布 parent 必须补齐：

- `name`
- `description`
- `url`
- `licenses`
- `scm`
- `developers`
- `issueManagement`
- `ciManagement` 可选

各模块应继承公共元数据，并保留模块自己的 `name` 与 `description`。

### 6.3 构建插件与 profile

发布 profile 至少覆盖：

- `maven-source-plugin` 生成 sources jar。
- `maven-javadoc-plugin` 生成 javadoc jar。
- `maven-gpg-plugin` 或 Maven Central 推荐签名方案。
- Central publish / deploy 插件。
- demo deploy skip。
- release dry-run 或 local staging 验证命令。

凭证规则：

- GPG 私钥、passphrase、Central token、用户名、密码不得写入仓库。
- GitHub Actions secret 只通过仓库 secret 注入。
- 本地 dry-run 不能依赖真实发布凭证。
- 缺少凭证或发布权限时，任务必须记录为 `BLOCKED_ENV` 或硬阻塞，不得伪造发布成功。

## 7. 测试矩阵

v1.0 release readiness 必须分层记录以下验证：

| 层级 | 命令 | 发布要求 |
| --- | --- | --- |
| 默认 Java 测试 | `cmd.exe /c mvn -B test` | 必须通过 |
| 编译 | `cmd.exe /c mvn -B -DskipTests compile` | 必须通过 |
| Store schema smoke | `cmd.exe /c mvn -B -pl reliable-task-store -am test` | 必须通过 |
| Testcontainers MySQL | `cmd.exe /c mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | 至少与本地 MySQL 二选一通过 |
| 本地 MySQL | `cmd.exe /c mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test` | 至少与 Testcontainers 二选一通过 |
| Console install/build/test | `cd reliable-task-console && npm ci && npm run typecheck && npm run lint && npm run test -- --run && npm run build` | v1.0 release notes 需记录，console 不阻塞 Central artifact 发布 |
| Console smoke | `cd reliable-task-console && npm run test:smoke` | 建议发布前执行；环境阻塞必须写清 |
| 发布 dry-run | Maven Central dry-run 或本地 staging 命令 | 必须执行或写清凭证阻塞 |
| 静态检查 | CodeQL 或等价静态检查 | 必须配置并记录 |
| 依赖安全扫描 | Maven/Node 依赖扫描 | 必须配置并记录 |
| whitespace | `git diff --check` | 必须通过 |

所有验证结果只允许使用 `PASS`、`FAIL_CODE`、`BLOCKED_ENV`、`NOT_RUN` 分层，不允许把未执行命令写成通过。

## 8. 示例矩阵

v1.0 文档和 demo 必须覆盖：

- 成功任务。
- 失败后重试。
- 不可重试失败进入 DEAD。
- 显式 `idempotencyKey`。
- Handler 幂等责任和外部副作用保护。
- `TaskDeadLetterHandler` 接入。
- Admin 只读查询。
- Admin 写操作安全门禁。
- 监控指标和 Prometheus 示例。
- `schema.sql`、Flyway、Liquibase 三选一初始化。
- Worker-only starter 和 Admin starter 显式 opt-in。
- Console preview 独立部署和 payload 安全边界。

## 9. 发布治理

v1.0 发布前必须完成：

- `CHANGELOG.md` 新增 `[1.0.0]` 条目。
- `docs/releases/v1.0.0.md` 面向用户说明 Highlights、Breaking Changes、Migration、Getting Started、Validation、Known Limits。
- `docs/v1.0/RELIABLE_TASK_V10_READINESS_REPORT.md` 作为事实来源，记录所有验证和未验证风险。
- `docs/release-process.md` 更新 Maven Central、GitHub Release、Central post-release 验证步骤。
- README 和中文 README 将 Maven Central 安装方式从 preview/source build 调整为 Central 依赖优先。
- `SECURITY.md`、`CONTRIBUTING.md`、Issue/PR 模板和 license 状态复核。
- 敏感信息扫描，确认没有真实 `.env`、`application.yml`、Token、cookie、私钥、生产地址或真实用户数据。

## 10. 验收标准

v1.0.0 只有在以下条件满足时才能进入 release closure：

- 用户可以从 Maven Central 直接依赖 `reliable-task-spring-boot-starter`。
- README、中文 README、demo、release notes 对 at-least-once、Handler 幂等责任、Admin 安全边界和 Console preview 边界表达一致。
- 核心可靠性场景已有自动化测试覆盖，并在 readiness report 中记录真实验证结果。
- v1.0 API/SPI 稳定范围明确，deprecated 和兼容策略清楚。
- 未承诺能力被明确列入暂缓方向。
- Maven Central 发布和 GitHub Release 均有可复验记录；如果凭证或人工授权缺失，协议必须阻塞在发布任务，不得伪造完成。
