# ReliableTask 开源前检查报告

检查日期：2026-05-05

## 仓库概况

- 项目类型：Java 21 / Maven 多模块 / Spring Boot 3.2.5。
- 核心模块：`reliable-task-core`、`reliable-task-store`、`reliable-task-executor`、`reliable-task-admin`、`reliable-task-spring-boot-starter`、`reliable-task-demo`。
- 开源许可证：Apache License 2.0。
- 首次公开版本：`v0.1.0`。
- 当前发布方式：`main` 分支 + annotated tag，不创建额外 release 分支。

## 已补齐的开源文件

- `README.md`
- `LICENSE`
- `CHANGELOG.md`
- `CONTRIBUTING.md`
- `SECURITY.md`
- `.env.example`
- `.gitignore`
- `.github/pull_request_template.md`
- `.github/ISSUE_TEMPLATE/bug_report.md`
- `.github/ISSUE_TEMPLATE/feature_request.md`
- `.github/workflows/ci.yml`
- `docs/release-process.md`
- `docs/releases/v0.1.0.md`

## 敏感信息检查

当前只读扫描发现并处理了 `.env.example` 中的非占位符数据库密码风险，已替换为 `change_me`。

已确认的非敏感命中项：

- `localhost`：Demo 和 README 的本地运行示例。
- `change_me`：示例密码占位符。
- `user@example.com`：文档中的示例邮箱。
- `ORD-001`、`USER-123`：Demo curl 示例数据。
- `https://github.com/naruto863/reliable-task/security/advisories/new`：公开 GitHub Security Advisory 提交入口。

## 不应提交文件

以下文件或目录存在于本地工作区，但应保持忽略：

- `.idea/`
- `.m2/`
- 各模块 `target/`
- 各模块 Eclipse 元数据：`.classpath`、`.project`、`.factorypath`、`.settings/`
- 本地 `.env`
- 本地 `application.yml`、`application.yaml`、`application.properties`

## CI 与测试

- 基础 CI：`.github/workflows/ci.yml` 使用 Ubuntu、Temurin JDK 21、Maven cache，并执行 `mvn -B test`。
- 本地构建状态：`mvn -B -DskipTests compile` 已通过，所有 Maven 模块主代码编译成功。
- 本地测试状态：当前环境执行 `mvn -B test` 未通过验证，失败点在 Maven Surefire provider 依赖解析和本地 Maven 仓库写入权限，不是业务测试断言失败。
- 复验现象：
  - `D:\Code\Code\MavenRepo` 写入 `*.lastUpdated` / `*.part.lock` 被拒绝。
  - 工作区 `.m2\repository` 下访问 `maven.aliyun.com` 解析 Spring Boot BOM 失败。
  - 离线模式缺少 `org.apache.maven.surefire:surefire-junit-platform:3.5.2`。
- 结论：发布前必须在网络和 Maven 仓库权限正常的环境，或 GitHub Actions 中重新执行 `mvn -B test` 并确认通过。

## 发布前剩余 TODO

- [ ] 在可联网且 Maven 仓库可写的环境执行 `mvn -B test` 并确认通过。
- [ ] 确认远端仓库权限可以读取和推送 `origin`。
- [ ] 确认远端 `v0.1.0` tag 尚不存在。
- [ ] 创建 annotated tag 前再次确认无真实密钥、Token、Cookie、内部 URL 或真实用户数据。

## 综合结论

仓库已具备首次开源预览的基础治理结构，发布准备文件已按 `v0.1.0` 对齐。当前不建议跳过测试直接发布；最小发布门槛是让 `mvn -B test` 在干净环境或 GitHub Actions 中通过，并确认远端 tag 状态。
