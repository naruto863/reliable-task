# ReliableTask v1.0 API/SPI Compatibility Policy

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- source_scope: `docs/v1.0/RELIABLE_TASK_V10_SCOPE.md`
- created_at: 2026-06-14 +08:00

## 1. Compatibility Goal

v1.0.0 freezes the API/SPI surface that external Spring Boot applications, custom stores, handlers and operational integrations are expected to use during the v1.x line.

The v1.x compatibility goal is source compatibility first:

- Existing v0.x users should not be forced to delete or rewrite working handler, store, serializer, payload or starter integration code during v1.0 adoption.
- New extension points may be added through default methods, new optional types, additional overloads or opt-in configuration.
- Breaking method signature, enum code, configuration key or schema changes must be treated as release-level compatibility events and documented in the upgrade guide, CHANGELOG and release notes.

Runtime behavior still follows ReliableTask's database-backed at-least-once model. This policy does not add exactly-once guarantees.

## 2. Stable API/SPI Surface

### 2.1 Submission and Handler APIs

Stable v1.x surfaces:

- `com.reliabletask.core.spi.TaskTemplate`
- `com.reliabletask.core.model.TaskSubmitRequest`
- `com.reliabletask.core.model.TaskSubmitResult`
- `com.reliabletask.core.spi.TaskHandler`
- `com.reliabletask.core.annotation.TaskHandler`
- `com.reliabletask.core.annotation.TaskRetryable`
- `com.reliabletask.core.enums.RetryStrategyType`

Compatibility rules:

- Keep existing `TaskTemplate` submit, object-payload submit, delay submit, batch submit and structured-result methods source-compatible.
- Keep `TaskSubmitRequest.idempotencyKey` mapped to the effective `bizUniqueKey`; it remains the explicit business idempotency key and must not be used for credentials, tokens or raw personal identifiers.
- Keep `TaskHandler.execute(TaskInstance)` as the minimum handler contract.
- Keep typed payload handling, `maxConcurrency()` and `timeoutMs()` as default methods so older handlers keep compiling.
- Keep `@TaskHandler` as a core annotation without requiring core to depend on Spring.
- Keep `RetryStrategyType.CUSTOM` failing fast when no matching strategy is registered; do not silently fall back to another strategy.

### 2.2 State Machine and Status Codes

Stable v1.x surfaces:

- `com.reliabletask.core.enums.TaskStatus`
- `com.reliabletask.core.lifecycle.TaskStateMachine`
- `com.reliabletask.core.model.TaskExecutionLease`

Compatibility rules:

- Keep status names and integer codes stable: `PENDING=0`, `RUNNING=1`, `SUCCESS=2`, `FAILED=3`, `RETRYING=4`, `DEAD=5`, `CANCELLED=6`.
- Keep `TaskStateMachine` as the single public lifecycle transition reference.
- Keep `FAILED` as a compatibility value and execution-log result, not the preferred automatic task lifecycle target.
- Keep `TaskExecutionLease` available for CAS-style success, retry, dead and recovery writes.

### 2.3 Events, Failure Classification and Diagnostics

Stable v1.x surfaces:

- `TaskEvent`
- `TaskEventType`
- `TaskEventListener`
- `FailureClassifier`
- `FailureDecision`
- `TaskExceptionFormatter`
- `TaskFailureDiagnostic`
- `TaskDeadLetterHandler`
- `DeadLetterContext`

Compatibility rules:

- Keep event publishing lightweight and synchronous with listener exception isolation.
- Keep failure classification limited to `RETRY` or `DEAD` decisions.
- Keep diagnostic formatting customizable so users can redact, shorten or classify errors before persistence.
- Keep dead-letter handling post-DEAD and exception-isolated. A failed dead-letter handler must not undo the task state write.
- Do not describe lifecycle events as an event-sourcing log, durable async event queue or exactly-once mechanism.

### 2.4 Store API

Stable v1.x surfaces:

- `TaskCommandStore`
- `TaskQueryStore`
- `TaskOperationsStore`
- `TaskStore`

Compatibility rules:

- New framework code should depend on the narrowest interface that matches its responsibility.
- `TaskCommandStore` owns task creation, claim, lease, state writes, recovery and execution logs.
- `TaskQueryStore` owns Admin read models, stats, logs and operational query views.
- `TaskOperationsStore` owns manual operations, audit, batch operations and worker heartbeat.
- `TaskStore` remains the compatibility facade extending all three narrow interfaces and must not be removed in v1.0.
- New Store methods should use default methods where practical so custom implementations remain source-compatible.

### 2.5 Payload API

Stable v1.x surfaces:

- `TaskPayloadSerializer`
- `TaskPayloadCodec`
- `TaskPayloadCodecContext`
- `TaskSerializer`

Compatibility rules:

- New integrations should prefer `TaskPayloadSerializer` for simple payload conversion or `TaskPayloadCodec` for context-aware conversion.
- `TaskPayloadCodecContext` may use existing task metadata but v1.0 must not require new payload-related schema columns.
- `TaskSerializer` remains deprecated and retained. It must keep `@Deprecated(since = "0.6.0", forRemoval = false)` during v1.0.
- `TaskSerializer` should not receive new capabilities; compatibility adapters should bridge old serializer implementations to the current codec path.

### 2.6 Starter and Configuration API

Stable v1.x surfaces:

- `reliable-task-spring-boot-starter`
- `reliable-task-admin-spring-boot-starter`
- `ReliableTaskProperties`
- `META-INF/additional-spring-configuration-metadata.json`

Compatibility rules:

- Keep `reliable-task-spring-boot-starter` as Worker-only. It must not bring Admin REST/Web dependencies by default.
- Keep `reliable-task-admin-spring-boot-starter` as explicit opt-in for Admin REST APIs and Web dependencies.
- Keep Admin REST disabled by default: `reliable-task.admin.enabled=false`.
- Keep Admin writes disabled by default: `reliable-task.admin.write-enabled=false`.
- Keep Admin authorization enabled by default when Admin is registered: `reliable-task.admin.auth.enabled=true`.
- Keep audit and batch operations opt-in.
- Keep Console payload plaintext disabled by default.
- Keep `reliable-task.executor.mode=platform` as the default. The `virtual` mode is a safe opt-in configuration key that uses JDK 21 virtual threads and does not change default production behavior.
- Keep reserved settings bindable but document their current no-op behavior: `serializer.type`, `store.table-prefix`, `admin.port`, `admin.context-path`.

## 3. Deprecated and Compatibility-Retained Surfaces

| Surface | Status in v1.0 | Migration Path |
| --- | --- | --- |
| `TaskSerializer` | Deprecated, retained, not for removal. | Prefer `TaskPayloadSerializer`; use `TaskPayloadCodec` for context-aware encoding/decoding. |
| `TaskStore` | Compatibility facade retained. | Prefer `TaskCommandStore`, `TaskQueryStore` or `TaskOperationsStore` in new code. |
| `reliable-task.serializer.type` | Reserved bindable config. | Provide `TaskPayloadCodec` or `TaskPayloadSerializer` bean instead of relying on this string to switch behavior. |
| `reliable-task.store.table-prefix` | Reserved bindable config. | Current MyBatis implementation uses fixed table names. |
| `reliable-task.admin.port` | Reserved bindable config. | Admin APIs currently use the host application server port. |
| `reliable-task.admin.context-path` | Reserved bindable config. | Current REST mapping remains `/api/reliable-task`. |

No compatibility-retained surface should be deleted during the v1.0 protocol. If a later v1.x version proposes removal, it must be planned as a separate compatibility review and migration note.

## 4. Allowed Evolution During v1.x

Allowed without breaking v1.x source compatibility:

- Add new default methods to SPI interfaces when existing implementations keep compiling.
- Add new optional DTO fields when existing constructors/builders/defaults keep working.
- Add new configuration keys with safe defaults.
- Add new event types only when old listeners remain source-compatible and docs clarify semantics.
- Add new Store helper methods as defaults or through new narrow interfaces.
- Add new tests and docs that clarify existing behavior.

Requires compatibility review:

- Renaming or removing public methods, enum values, DTO fields, annotations or configuration keys.
- Changing `TaskStatus` integer codes.
- Changing Admin security defaults.
- Changing schema columns, indexes, unique keys or migration assumptions.
- Changing starter dependency boundaries.
- Changing payload persistence format in a way existing tasks cannot be decoded.

## 5. Test Evidence

Existing and added tests that protect the v1.0 compatibility surface:

| Test | Compatibility Evidence |
| --- | --- |
| `TaskSerializerCompatibilityTest` | Confirms legacy `TaskSerializer` implementations still compile and execute; v1.0 adds a guard that `TaskSerializer` remains deprecated with `forRemoval=false`. |
| `TaskStoreBoundaryCompatibilityTest` | Confirms old broad `TaskStore` implementations can be assigned to `TaskCommandStore`, `TaskQueryStore` and `TaskOperationsStore`, and that the facade declares no extra abstract methods. |
| `TaskStoreLeaseAwareDefaultsTest` | Confirms lease-aware default methods delegate to old task-id methods and null leases do not call legacy writes. |
| `TaskPayloadCodecAdaptersTest` | Confirms serializer-to-codec and codec-to-serializer adapters preserve payload compatibility. |
| `TaskExecutorFactoryTest` | Confirms platform executor compatibility and opt-in virtual-thread capacity semantics. |
| `TaskStatusTest` | Confirms status code mapping, terminal status and executable status behavior. |
| `TaskStateMachineTest` | Confirms allowed Worker lifecycle transitions, manual requeue transitions and illegal transition rejection. |
| `V2CoreSpiTest` | Confirms default/no-op SPI behavior for idempotency, audit, authorization, heartbeat and metrics remains compatible. |
| `ReliableTaskExecutorAutoConfigurationTest` | Confirms Worker starter defaults, custom SPI override behavior, payload codec priority, handler metadata, trace/name resolver and configuration binding. |
| `ReliableTaskAdminAutoConfigurationTest` | Confirms Admin default-disabled behavior, auth default, write preconditions, query settings and Console capability settings. |

TASK-002 validation must run:

- `cmd.exe /c mvn -B -pl reliable-task-core -am test`
- `cmd.exe /c mvn -B -pl reliable-task-spring-boot-starter,reliable-task-admin-spring-boot-starter -am test`
- `cmd.exe /c mvn -B -DskipTests compile`
- `git diff --check`

## 6. Documentation Follow-Ups

Later v1.0 tasks must keep this policy aligned with:

- `docs/migration/v1.0-upgrade-guide.md`
- README and `README.zh-CN.md`
- `CHANGELOG.md`
- `docs/releases/v1.0.0.md`
- `docs/v1.0/RELIABLE_TASK_V10_READINESS_REPORT.md`
- Spring Boot configuration metadata

Current documentation baseline: Java 21+, Spring Boot 3.5.14 and MyBatis-Plus 3.5.6.
