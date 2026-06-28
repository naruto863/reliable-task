# 本地开发

本地开发分为 Java 后端、demo MySQL、console 前端和可选集成测试。默认验证不需要 Docker 或本地 MySQL；真实 MySQL 集成测试必须使用专用、可丢弃的测试库。

## 环境要求

| 工具 | 版本 |
| --- | --- |
| JDK | 21+ |
| Maven | 3.8+ |
| MySQL | 8.0+，运行 demo 或 MySQL IT 时需要 |
| Node.js | 22，运行 console 时建议与 CI 一致 |

## 默认后端验证

Windows/PowerShell 下建议通过 `cmd.exe /c` 执行 Maven，避免 `-D...` 参数被 PowerShell 误解析。

```powershell
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B test"
```

这会运行默认单元测试、自动配置测试和 H2/schema 相关测试，不需要 Docker 或本地 MySQL。

## 运行 demo

1. 创建本地 MySQL 库。

```sql
CREATE DATABASE reliable_task DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 初始化 schema。

```powershell
mysql -u reliable_task_user -p reliable_task < reliable-task-store/src/main/resources/db/schema.sql
```

3. 准备本地配置。

```powershell
Copy-Item reliable-task-demo\src\main\resources\application-example.yml reliable-task-demo\src\main\resources\application.yml
```

4. 启动 demo。

```powershell
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -pl reliable-task-demo -am spring-boot:run"
```

5. 发起示例请求。

```powershell
curl -X POST "http://localhost:8080/demo/order?orderNo=ORD-001&buyerId=USER-123"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/stats"
```

## 运行 console

Console 是独立前端工程：

```powershell
cd reliable-task-console
npm ci
npm run dev
```

默认打开 `http://localhost:5173`。Vite 会把 `/api/reliable-task` 代理到 `VITE_RELIABLE_TASK_PROXY_TARGET`，示例值在 `reliable-task-console/.env.example`。

常用验证：

```powershell
cd reliable-task-console
npm run typecheck
npm run test
npm run build
```

## MySQL 集成测试

Testcontainers profile：

```powershell
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test"
```

本地 MySQL profile 需要手动设置专用测试库变量，且只能指向可清理的测试库：

```powershell
$env:RELIABLE_TASK_IT_JDBC_URL="jdbc:mysql://127.0.0.1:3306/reliable_task_it?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
$env:RELIABLE_TASK_IT_USERNAME="reliable_task_it"
$env:RELIABLE_TASK_IT_PASSWORD="change_me"
$env:RELIABLE_TASK_IT_MYSQL_MODE="local"
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test"
```

如果 Docker、MySQL、网络、npm registry 或凭据不可用，应记录为 `BLOCKED_ENV`，不要写成通过。

## CI 对应关系

| 本地命令 | CI job |
| --- | --- |
| `mvn -B test` | `maven-test` |
| `npm ci && npm run typecheck && npm run test -- --run && npm run build` | `console-test` |
| `mvn -B -Pmysql-it ... test` | 手动 `mysql-it` |
| `npm run test:smoke` | 手动 `console-smoke` |

