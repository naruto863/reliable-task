# ReliableTask Demo

这是 ReliableTask 的演示工程，用于展示 Spring Boot 应用如何接入可靠异步任务能力。

Demo 覆盖：

- 在业务事务中投递任务。
- Worker 拉取并执行任务。
- 可重试失败、不可重试失败、重复投递、对象 payload。
- Admin API 查询任务、统计、Worker 状态。
- Micrometer 指标查询。

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

## 启动

从仓库根目录执行：

```bash
mvn -pl reliable-task-demo -am spring-boot:run
```

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

## 安全提醒

- `X-Operator: admin` 只用于本地 Demo。
- 生产环境不要直接暴露 Admin 写接口。
- 生产环境必须接入认证、授权、审计、网络访问控制和监控告警。
- 不要把真实账号、密码、Token 或内部地址写入 `application-example.yml`。
