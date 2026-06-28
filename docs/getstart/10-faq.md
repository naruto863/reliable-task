# FAQ

## ReliableTask 是消息队列吗

不是。ReliableTask 是 MySQL-backed reliable task/outbox 组件，适合业务事务提交后可靠触发内部后续任务。它不提供通用 pub/sub、流处理、广播消费或超高吞吐缓冲。

## 是否提供 exactly-once

不提供。ReliableTask 提供 at-least-once 执行语义。任务状态、租约 CAS 和幂等投递可以减少重复状态写，但外部 HTTP/RPC/MQ/支付/发货等副作用仍必须由业务 handler 自己做幂等。

## 为什么使用 MySQL

当前实现围绕 MySQL 表、索引、唯一键、条件更新和本地事务设计。这样业务数据和任务记录可以在同一个数据库事务里提交或回滚。仓库里没有 Redis、MQ 或其他生产存储实现。

## Admin API 默认安全吗

Admin API 默认不会仅因为引入 worker starter 而暴露。需要显式引入 `reliable-task-admin-spring-boot-starter` 并设置 `reliable-task.admin.enabled=true`。

写操作还要求：

- `reliable-task.admin.write-enabled=true`；
- `reliable-task.admin.auth.enabled=true`；
- `reliable-task.admin.audit.enabled=true`；
- `X-Confirm-Operation: true`；
- 授权 provider 允许当前 operator 执行动作。

## Console 是否必需

不必需。Console 是独立 Vite/Vue 运维 UI，用于查看 Dashboard、任务、详情、Worker、审计和受保护操作。任务投递和执行不依赖 Console。

## 如何新增任务类型

实现 `com.reliabletask.core.spi.TaskHandler`，返回稳定 `taskType`，并在业务事务内通过 `TaskTemplate.submit(...)` 投递任务。需要重试策略时添加 `@TaskRetryable`。

## 如何处理不可重试错误

可以抛出 `NonRetryableException`，默认 `FailureClassifier` 会将其判定为 DEAD。更复杂的业务规则可以提供自定义 `FailureClassifier` Bean。

## payload 可以保存敏感信息吗

不建议。不要在 payload、错误信息、审计摘要、idempotency key 或日志里保存凭据、Token、私钥或原始敏感个人信息。Console 默认只返回 payload preview，不返回明文。

## 本地测试需要 MySQL 吗

默认 `mvn -B test` 不需要 MySQL。运行 demo 或 MySQL 集成测试需要 MySQL。真实 MySQL 集成测试必须指向专用、可清理的测试库。

## Maven Central 是否可用

README 当前说明 `v1.0.0` 正在准备稳定开源发布，Maven Central 安装路径只有在 release closure 确认发布后才能视为可用。发布前可使用源码构建、本地 Maven install 或私有仓库。

