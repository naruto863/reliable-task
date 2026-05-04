# ReliableTask

ReliableTask 是一个基于 Spring Boot 3 的可靠异步任务执行框架，面向“业务事务提交后需要稳定执行异步动作”的场景。

它提供事务内任务投递、数据库任务存储、Worker 调度、自动重试、超时补偿、线程池隔离、管理 API 和 Spring Boot Starter 自动装配能力。

[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> 当前项目处于首次开源预览阶段，建议版本为 `v0.1.0`。生产使用前请完成数据库备份、Admin 鉴权、监控告警和容量评估。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 事务内投递 | 在业务事务中写入任务，业务数据和任务记录同提交、同回滚 |
| 自动重试 | 支持固定间隔和指数退避策略，异常任务可按策略进入重试队列 |
| 补偿扫描 | 定时发现超时运行中的任务，降低 Worker 异常退出导致任务卡死的风险 |
| 线程池隔离 | 按任务类型配置线程池，避免单类热点任务拖垮所有异步执行 |
| 幂等支持 | 提供幂等策略 SPI 和默认策略，降低重复投递和重复执行风险 |
| 管理 API | 提供任务查询、重试、终止、重新入队、统计和 Worker 查询等接口 |
| Starter 接入 | 通过 Spring Boot 自动装配减少业务应用接入成本 |

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `reliable-task-core` | 领域模型、SPI、枚举、异常、重试策略 |
| `reliable-task-store` | MyBatis-Plus 存储实现和 MySQL 表结构 |
| `reliable-task-executor` | 任务执行、Worker 调度、重试、补偿、线程池 |
| `reliable-task-admin` | 管理 API、任务运维接口、指标收集 |
| `reliable-task-spring-boot-starter` | 自动装配和配置属性 |
| `reliable-task-demo` | 可运行示例工程 |

## 技术栈

| 技术 | 版本 |
| --- | --- |
| Java | 21+ |
| Maven | 3.8+ |
| Spring Boot | 3.2.5 |
| MyBatis-Plus | 3.5.6 |
| MySQL | 8.0+ |
| JUnit | 5 |

## 快速开始

### 1. 克隆仓库

```bash
git clone TODO_REPOSITORY_URL
cd reliabletask
```

### 2. 初始化数据库

```sql
CREATE DATABASE reliable_task DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u reliable_task_user -p reliable_task < reliable-task-store/src/main/resources/db/schema.sql
```

### 3. 准备 Demo 配置

`application.yml` 已被 `.gitignore` 忽略，请从示例文件生成本地配置并填入自己的数据库信息：

```bash
cp reliable-task-demo/src/main/resources/application-example.yml reliable-task-demo/src/main/resources/application.yml
```

推荐使用环境变量覆盖敏感配置：

```bash
export RELIABLE_TASK_DATASOURCE_URL="jdbc:mysql://localhost:3306/reliable_task?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
export RELIABLE_TASK_DATASOURCE_USERNAME="reliable_task_user"
export RELIABLE_TASK_DATASOURCE_PASSWORD="change_me"
```

### 4. 编译和测试

```bash
mvn -B test
```

### 5. 启动 Demo

```bash
mvn -pl reliable-task-demo -am spring-boot:run
```

### 6. 验证 Demo

```bash
curl -X POST "http://localhost:8080/demo/order?orderNo=ORD-001&buyerId=USER-123"
curl "http://localhost:8080/api/reliable-task/tasks"
curl "http://localhost:8080/api/reliable-task/tasks/stats"
```

## 接入方式

当前 `v0.1.0` 预览阶段默认通过源码构建后在本地或私有仓库引用。Maven Central 发布状态：TODO。

```xml
<dependency>
    <groupId>com.reliabletask</groupId>
    <artifactId>reliable-task-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 最小配置示例

```yaml
spring:
  datasource:
    url: ${RELIABLE_TASK_DATASOURCE_URL}
    username: ${RELIABLE_TASK_DATASOURCE_USERNAME}
    password: ${RELIABLE_TASK_DATASOURCE_PASSWORD}

reliable-task:
  enabled: true
  worker:
    enabled: true
  recovery:
    enabled: true
  admin:
    enabled: true
```

### 实现 TaskHandler

```java
@TaskHandler("SEND_EMAIL")
@TaskRetryable(maxRetryCount = 3, retryIntervalMs = 2000)
public class SendEmailHandler implements com.reliabletask.core.spi.TaskHandler {

    @Override
    public String getTaskType() {
        return "SEND_EMAIL";
    }

    @Override
    public void execute(TaskInstance task) throws Exception {
        // 在这里执行你的业务逻辑。
    }
}
```

### 在事务中投递任务

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final TaskTemplate taskTemplate;

    @Transactional
    public void createOrder(String orderNo) {
        // 1. 保存业务数据。
        // 2. 在同一个事务内投递异步任务。
        taskTemplate.submit(TaskSubmitRequest.builder()
            .taskType("SEND_EMAIL")
            .bizType("ORDER")
            .bizId(orderNo)
            .payload("{\"to\":\"user@example.com\"}")
            .build());
    }
}
```

## 配置说明

常用配置前缀为 `reliable-task`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `reliable-task.enabled` | `true` | 框架总开关 |
| `reliable-task.worker.enabled` | `true` | Worker 拉取和执行开关 |
| `reliable-task.worker.poll-interval-ms` | `5000` | Worker 拉取间隔 |
| `reliable-task.worker.batch-size` | `10` | 单次拉取数量 |
| `reliable-task.recovery.enabled` | `true` | 超时补偿扫描开关 |
| `reliable-task.metrics.enabled` | `false` | Micrometer 指标开关 |
| `reliable-task.alert.enabled` | `false` | 告警扫描开关 |
| `reliable-task.admin.enabled` | `true` | 管理 API 开关 |
| `reliable-task.admin.auth.enabled` | `false` | Admin 权限 SPI 开关 |
| `reliable-task.admin.audit.enabled` | `false` | Admin 操作审计开关 |
| `reliable-task.admin.batch.enabled` | `false` | 批量运维 API 开关 |

完整示例见 [application-example.yml](reliable-task-demo/src/main/resources/application-example.yml)。

## 安全说明

- 不要提交 `application.yml`、`.env`、真实数据库账号、真实密码、Token、Cookie 或内部地址。
- Demo 的 `application-example.yml` 只包含占位符，真实配置应放在本地环境变量或私有配置中心。
- Admin API 默认适合本地演示。生产环境必须启用鉴权、审计、网络访问控制，并限制写操作权限。
- 如果发现漏洞或敏感信息泄露，请按 [SECURITY.md](SECURITY.md) 处理，不要直接公开披露。

## 测试

```bash
mvn -B test
```

当前测试以单元测试和不依赖外部 MySQL 的 H2 schema 校验为主。需要真实 MySQL 的 Demo 验证请按“快速开始”准备本地数据库。

## 版本与发布

- 版本号遵循 SemVer。
- Git Tag 使用 `vX.Y.Z`，例如 `v0.1.0`。
- 变更记录维护在 [CHANGELOG.md](CHANGELOG.md)。
- 发布流程见 [docs/release-process.md](docs/release-process.md)。
- 开源前检查报告见 [docs/open-source-check-report.md](docs/open-source-check-report.md)。
- 首次开源发布建议使用 `v0.1.0` 预览版。

发布前示例命令：

```bash
mvn versions:set -DnewVersion=0.1.0
mvn -B test
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

## 贡献

欢迎通过 Issue 和 Pull Request 参与改进。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并在 PR 中说明变更范围、测试结果、安全影响和兼容性影响。

## License

ReliableTask 使用 [Apache License 2.0](LICENSE) 发布。
