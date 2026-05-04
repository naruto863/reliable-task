# ReliableTask 开源前检查报告

检查日期：2026-05-04

## 仓库概况

- 项目类型：Java 21 / Maven 多模块 / Spring Boot 3.2.5。
- 核心模块：`reliable-task-core`、`reliable-task-store`、`reliable-task-executor`、`reliable-task-admin`、`reliable-task-spring-boot-starter`、`reliable-task-demo`。
- 开源许可证：Apache License 2.0。
- 建议首次版本：`v0.1.0`。

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

## 敏感信息检查

当前只读扫描未发现明显真实密钥、Token、Cookie、真实账号、真实内部地址或真实个人数据。

已确认的非敏感命中项：

- `localhost`：Demo 和 README 的本地运行示例。
- `change_me`：示例密码占位符。
- `user@example.com`：文档中的示例邮箱。
- `TODO_REPOSITORY_URL`、`TODO_SECURITY_EMAIL`、`TODO_REPOSITORY_SECURITY_ADVISORY_URL`：待维护者发布前确认的占位符。
- `ORD-001`、`USER-123`：Demo curl 示例数据。

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
- 本地验证状态：当前环境执行 `mvn -B test` 时未进入编译阶段，失败原因是 Maven 访问 `maven.aliyun.com` 解析 Spring Boot BOM 失败。
- 结论：该失败更像本地 Maven mirror 或网络解析问题，不判定为业务测试失败；发布前必须在网络可用环境或 GitHub Actions 中复验。

## 发布前剩余 TODO

- [ ] 替换或确认 `README.md` 中的 `TODO_REPOSITORY_URL`。
- [ ] 替换或确认 `SECURITY.md` 中的 `TODO_SECURITY_EMAIL`。
- [ ] 替换或确认 `SECURITY.md` 中的 `TODO_REPOSITORY_SECURITY_ADVISORY_URL`。
- [ ] 如需固定版权主体，在发布前确认 Apache-2.0 版权归属说明。
- [ ] 在可联网环境执行 `mvn -B test` 并确认通过。
- [ ] 发布前将 POM 版本从 `0.1.0-SNAPSHOT` 调整为 `0.1.0`。

## 综合结论

仓库已具备首次开源预览的基础治理结构。下一步重点不是引入更多工具，而是完成发布前人工确认：安全联系方式、仓库地址、CI 测试结果、Tag 与 GitHub Release 内容。

