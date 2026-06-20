# ReliableTask Demo

这是 ReliableTask 的演示工程，用于展示 Spring Boot 应用如何接入可靠异步任务能力。

Demo 覆盖：

- 在业务事务中投递任务。
- Worker 拉取并执行任务。
- 可重试失败、不可重试失败、重复投递、对象 payload。
- Admin API 查询任务、统计、Worker 状态。
- Micrometer 指标查询。

v1.0 场景化示例索引见 [ReliableTask v1.0 Example Matrix](../docs/v1.0/RELIABLE_TASK_V10_EXAMPLE_MATRIX.md)，可按成功、失败、重试、幂等、死信、Admin 安全、监控和迁移场景查找入口。

Demo 只演示 ReliableTask 的投递、执行、重试和管理 API。它显式引入 `reliable-task-admin-spring-boot-starter` 才启用 Admin REST 自动装配；只引入 worker starter 的应用不会默认创建 Admin controller。生产环境仍必须按 at-least-once 语义设计 Handler：同一个业务动作可能因为重试、超时恢复或人工重新入队被执行多次，外部系统调用需要业务方自行保证幂等。

## 前置条件

| 工具 | 版本 |
| --- | --- |
| JDK | 21+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |

## 初始化数据库

```sql
CREATE DATABASE reliable_task DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u reliable_task_user -p reliable_task < reliable-task-store/src/main/resources/db/schema.sql
```

## 准备配置

Demo 的真实 `application.yml` 不提交到仓库。请从示例文件生成本地配置：

```bash
cp reliable-task-demo/src/main/resources/application-example.yml reliable-task-demo/src/main/resources/application.yml
```

也可以通过环境变量覆盖敏感配置：

```bash
export RELIABLE_TASK_DATASOURCE_URL="jdbc:mysql://localhost:3306/reliable_task?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
export RELIABLE_TASK_DATASOURCE_USERNAME="reliable_task_user"
export RELIABLE_TASK_DATASOURCE_PASSWORD="change_me"
```

`application-example.yml` 为了本地演示显式开启 Admin：`reliable-task.admin.enabled=true`、`write-enabled=true`、`auth.enabled=false`。示例同时保持 `audit.enabled=false` 和 `batch.enabled=false`，因此控制台预览版会展示只读排障视图，并禁用写操作按钮。生产默认仍是 Admin REST API 关闭、写接口关闭、权限检查开启。

## 本地演示与生产默认差异

- Demo 为了便于体验，显式启用 Admin 读写 API 并关闭权限检查；生产环境默认应保持 `reliable-task.admin.enabled=false`、`reliable-task.admin.write-enabled=false`、`reliable-task.admin.auth.enabled=true`。
- Demo 可以直接访问 `http://localhost:8080/api/reliable-task/**`；生产环境必须通过内部运维网络、认证、授权和审计访问 Admin API。
- Demo 使用示例账号、示例数据库和本地 curl；生产环境必须使用专用 MySQL、备份、监控告警、容量评估和真实 MySQL profile 验证。
- Demo payload 和错误示例不得替换成真实凭据、Token、内部地址或敏感用户数据。
- 生产排障请参考根目录 [README](../README.md) 的生产接入 Checklist，以及 [monitoring guide](../docs/operations/reliable-task-monitoring.md)、[runbook](../docs/operations/reliable-task-runbook.md) 和 [Prometheus alerts example](../docs/operations/prometheus-alerts-example.yml)。

## 启动

从仓库根目录执行：

```bash
mvn -pl reliable-task-demo -am spring-boot:run
```

## 启动 Web 控制台预览版

另开一个终端进入独立前端工程：

```bash
cd reliable-task-console
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite dev server 默认把 `/api/reliable-task` 代理到 `http://localhost:8080`，与上面的 Demo 后端端口一致。若后端端口不同，可以创建 `reliable-task-console/.env.local`：

```bash
VITE_RELIABLE_TASK_PROXY_TARGET=http://localhost:8080
```

控制台常见状态：

- `API unreachable`：Demo 后端未启动，或 Vite proxy target 不正确。
- `Admin disabled`：未引入 Admin starter，或未启用 `reliable-task.admin.enabled=true`。
- `Access denied`：后端权限检查拒绝当前 `X-Operator`。
- `Audit log is disabled`：示例默认 `audit.enabled=false`，审计页面和写操作安全门禁会显示禁用原因。
- `Reveal disabled`：示例默认不返回 payload 明文，详情页只展示 console-safe preview。

如需仅在本地演示写操作 UI，需要同时启用 `write-enabled=true`、`auth.enabled=true`、`audit.enabled=true`，并提供本地可用的授权实现和审计存储。不要把这种本地配置作为生产建议。

## 验证接口

```bash
# 下单并投递发货任务
curl -X POST "http://localhost:8080/demo/order?orderNo=ORD-001&buyerId=USER-123"

# 不可重试失败示例，任务直接进入 DEAD
curl -X POST "http://localhost:8080/demo/order/non-retryable?orderNo=ORD-BAD-001&buyerId=USER-123"

# 重复投递示例，两次返回同一个 taskId
curl -X POST "http://localhost:8080/demo/order/duplicate?orderNo=ORD-DUP-001&buyerId=USER-123"

# 对象 payload 示例
curl -X POST "http://localhost:8080/demo/order/object-payload?orderNo=ORD-OBJ-001&buyerId=USER-123&address=Shanghai"

# 查看任务列表
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks"

# 查看任务详情
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/1"

# 查看统计数据
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/stats"

# 查看 v0.5 运维查询
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/recent-failures?limit=20"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/slow?durationMsGte=30000&limit=20"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/failure-top?groupBy=taskType,errorCode&limit=20"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/1/timeline"

# 查看 Worker 实例和失联 Worker
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/workers"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/workers/stale"

# 查看 Actuator 指标
curl "http://localhost:8080/actuator/metrics/reliable_task_pending_total"
```

也可以运行脚本：

```powershell
reliable-task-demo/scripts/v2-demo-curl.ps1 -BaseUrl http://localhost:8080
```

## 幂等边界

- `/demo/order/duplicate` 演示的是投递幂等：示例代码使用 `TaskSubmitRequest.idempotencyKey("shipment:order:" + orderNo)`，相同 key 会命中同一个任务记录。
- 投递幂等不等于执行端 exactly-once。`TaskHandler` 仍可能在失败重试、超时恢复或人工重新入队后再次执行。
- 生产 Handler 应使用稳定业务键保护外部副作用，例如把 `orderNo` 作为发货、发券、发邮件或第三方请求的幂等键。
- `idempotencyKey` 会作为 `bizUniqueKey` 入库，不能为空白且不能超过 256 字符；不要放入手机号、身份证、Token 或凭证原文。
- 对不支持幂等键的外部系统，建议先写本地操作流水或副作用表，并用唯一约束避免重复调用。

## 安全提醒

- `X-Operator: admin` 只用于本地 Demo。
- Demo 配置中显式开启了 `reliable-task.admin.enabled=true`、`write-enabled=true`，并关闭了 `auth.enabled`，只用于本地体验。
- 生产环境不要直接暴露 Admin REST API，默认应保持 `reliable-task.admin.enabled=false`、`write-enabled=false` 和 `auth.enabled=true`。
- 生产环境必须接入认证、授权、审计、网络访问控制和监控告警。
- 不要把真实账号、密码、Token 或内部地址写入 `application-example.yml`。
