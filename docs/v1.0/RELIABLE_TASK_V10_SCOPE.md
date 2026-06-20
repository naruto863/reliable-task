# ReliableTask v1.0 Scope and API Freeze Inventory

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- source_spec: `docs/v1.0/RELIABLE_TASK_V10_STABLE_OPEN_SOURCE_TECH_SPEC.md`
- protocol: `plans/codex_app/reliable-task/v1.0`
- created_at: 2026-06-14 +08:00

## 1. Scope Decision

ReliableTask v1.0.0 is a stable open-source closure release on top of the v0.5, v0.6 and v0.7 preview lines.

The goal is not to add another large feature set. The goal is to make the existing database-backed reliable task model safe to publish and maintain as a v1.x contract:

- Freeze the core user-facing API/SPI, configuration keys, task state machine and reliability semantics.
- Document the compatibility path from v0.x to v1.0, including deprecated interfaces retained for source compatibility.
- Stabilize schema initialization and migration guidance for `schema.sql`, Flyway and Liquibase.
- Prepare Maven Central publication without committing credentials or claiming an unverified release.
- Record the Java, MySQL, Console, release dry-run and security validation matrix with explicit `PASS`, `FAIL_CODE`, `BLOCKED_ENV` and `NOT_RUN` states.

## 2. Current Baseline

| Area | Baseline Fact |
| --- | --- |
| Version | Root and module POMs are currently `0.7.0`. |
| Java build baseline | Root POM sets `java.version`, `maven.compiler.source` and `maven.compiler.target` to `21`. |
| Spring baseline | Spring Boot dependency management is `3.5.14`. |
| Maven reactor | Core Java reactor includes `reliable-task-core`, `reliable-task-store`, `reliable-task-executor`, `reliable-task-admin`, `reliable-task-spring-boot-starter`, `reliable-task-admin-spring-boot-starter` and `reliable-task-demo`. |
| Console | `reliable-task-console` is an independent Vue/Vite preview and is not in the Maven reactor. |
| Storage baseline | MyBatis-Plus/MySQL store with `schema.sql`, Flyway initial migration and Liquibase initial changelog. |
| Publication baseline | Maven Central metadata, sources/javadoc/signing/deploy profile and release dry-run workflow are prepared, but publication is not authorized in this protocol. |
| Public docs baseline | README and Chinese README describe `v0.7.0` as the preview source baseline and keep Maven Central availability pending release closure. |

Note: README badges and requirements now align on Java 21+ and Spring Boot 3.5.14.

## 3. In Scope for v1.0

The following items are in the v1.0 release closure:

1. Core API/SPI freeze and compatibility documentation.
2. Deprecated-but-retained compatibility statement for `TaskStore` and `TaskSerializer`.
3. At-least-once reliability semantics and Handler idempotency guidance.
4. Schema initialization strategy and v0.x to v1.0 migration guide.
5. Maven Central publication metadata, release profile, sources jar, javadoc jar and signing/deploy configuration.
6. Release dry-run workflow and manual publication process using secrets only.
7. Java default tests, MySQL profile validation, migration smoke, Admin safety tests, Console typecheck/lint/unit/build/smoke, static checks and dependency/security scan matrix.
8. Example matrix for successful tasks, retry, non-retryable failure, dead-letter handling, explicit idempotency key, Admin safety and schema initialization.
9. README, Chinese README, demo README, console README, operations docs, CHANGELOG and release notes alignment.
10. v1.0 readiness report and final release notes.

## 4. Explicit Non-Scope

The following items must not block or expand v1.0:

- exactly-once execution or exactly-once external side effects.
- Official PostgreSQL, MongoDB, Redis, Kafka, RabbitMQ, RocketMQ, XXL-Job or other backend implementations.
- Redis/Redisson `TaskLockStrategy` official implementation.
- DAG, task dependency, workflow orchestration, approval flow or Saga engine.
- Complex multi-tenant RBAC platform, organization menu system or cross-application control plane.
- Bundling `reliable-task-console` into worker/admin starters, demo jar or Maven reactor.
- Making Console production-grade enterprise SSO/RBAC/public-network hardening.
- Removing v0.x compatibility facades in v1.0.

## 5. Freeze Levels

| Level | Meaning | v1.0 Rule |
| --- | --- | --- |
| `STABLE` | User-facing contract should remain source-compatible during v1.x. | Changes require a compatibility review, tests and release notes. |
| `COMPATIBILITY_RETAINED` | Existing API remains for source compatibility but is no longer the preferred extension point. | Do not remove in v1.0; document migration path. |
| `RESERVED` | Config or model field is bindable/present but behavior is intentionally limited. | Keep bindability and document that no behavior is currently wired. |
| `PREVIEW` | Useful but not part of core library stability promise. | Keep separate from core artifacts and document caveats. |

## 6. API/SPI Freeze Inventory

### 6.1 Core Submission and Handler API

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `TaskTemplate` | `STABLE` | Keep submit, object-payload submit, structured submit result, delay submit and batch submit signatures source-compatible. |
| `TaskSubmitRequest` | `STABLE` | Keep `taskType`, `bizType`, `bizId`, `payload`, `payloadObject`, `idempotencyStrategy`, `idempotencyKey`, retry, priority, shard and tenant fields compatible. |
| `TaskSubmitResult` | `STABLE` | Keep structured result fields and `getResultId()` behavior compatible. |
| `com.reliabletask.core.spi.TaskHandler` | `STABLE` | Keep `getTaskType()`, `execute(TaskInstance)`, default `payloadType()`, default typed `execute`, `maxConcurrency()` and `timeoutMs()` source-compatible. |
| `@TaskHandler` | `STABLE` | Keep annotation on handler types with global task type value. Core remains Spring-free. |
| `@TaskRetryable` | `STABLE` | Keep handler-level retry override fields and defaults compatible. |
| `RetryStrategyType` | `STABLE` | Keep `FIXED`, `EXPONENTIAL` and `CUSTOM`; new strategies must not change existing semantics. |

Compatibility notes:

- `TaskSubmitRequest.idempotencyKey` maps to `bizUniqueKey`; it must stay stable, trimmed, non-blank when provided and no longer than the schema limit of 256 characters.
- Handler execution remains at-least-once. v1.0 docs must keep saying that external side effects require business idempotency.
- Adding new optional request fields is allowed only if old builders, constructors and defaults remain source-compatible.

### 6.2 State Machine and Reliability Semantics

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `TaskStatus` | `STABLE` | Keep current status codes and names stable: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `RETRYING`, `DEAD`, `CANCELLED`. |
| `TaskStateMachine` | `STABLE` | Keep it as the single public source for legal lifecycle transitions. |
| `TaskExecutionLease` | `STABLE` | Keep lease fields for CAS-style state writes: task id, worker id, lock time, lock expiry and version. |

Compatibility notes:

- `FAILED` is retained as a compatibility status/log result, not a normal v1.0 task lifecycle target.
- `DEAD` and `CANCELLED` may be manually requeued to `PENDING`; this does not imply exactly-once.
- Recovery is based on expired running task leases and must not be documented as proof that old external side effects stopped.

### 6.3 Events, Failure Classification and Diagnostics

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `TaskEvent` | `STABLE` | Keep lightweight event fields for task id, type, biz fields, before/after status, reason, worker, trace and event time. |
| `TaskEventType` | `STABLE` | Keep submitted, started, succeeded, retry scheduled, dead, cancelled, requeued and recovered event names. |
| `TaskEventListener` | `STABLE` | Keep synchronous listener SPI with exception isolation. |
| `FailureClassifier` | `STABLE` | Keep retry/dead classification extension point. |
| `FailureDecision` | `STABLE` | Keep `RETRY` and `DEAD` actions and factory methods. |
| `TaskExceptionFormatter` | `STABLE` | Keep diagnostic formatter SPI for error code, summary and stack trace. |
| `TaskFailureDiagnostic` | `STABLE` | Keep error code, error message and stack trace fields. |
| `TaskDeadLetterHandler` | `STABLE` | Keep post-DEAD extension point with default `getName()` and `handle(DeadLetterContext)`. |
| `DeadLetterContext` | `STABLE` | Keep task, error code/message, reason, retry exhaustion flag, source and dead time fields. |

Compatibility notes:

- Events are lightweight lifecycle notifications, not a full event-sourcing store or async event bus.
- Listener/classifier/formatter/dead-letter handler exceptions must stay isolated from task final state writes.
- Diagnostics and dead-letter examples must not encourage storing credentials, tokens, private keys, raw personal identifiers or production-sensitive data.

### 6.4 Store API

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `TaskCommandStore` | `STABLE` | Preferred write/execution path for submit, claim, lease, success, retry, dead, recovery and execution logs. |
| `TaskQueryStore` | `STABLE` | Preferred read path for Admin, stats, logs and operational query views. |
| `TaskOperationsStore` | `STABLE` | Preferred operations path for manual actions, audit, batch operations and worker heartbeat. |
| `TaskStore` | `COMPATIBILITY_RETAINED` | Keep as facade extending the three narrow interfaces; do not remove in v1.0. |

Compatibility notes:

- New code should depend on the narrowest Store interface it needs.
- Existing custom `TaskStore` implementations must not be forced to migrate during v1.0.
- Added Store methods should use default methods when possible to preserve custom implementation source compatibility.

### 6.5 Payload API

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `TaskPayloadSerializer` | `STABLE` | Keep simple serialize/deserialize SPI. |
| `TaskPayloadCodec` | `STABLE` | Keep context-aware encode/decode SPI without requiring schema changes. |
| `TaskPayloadCodecContext` | `STABLE` | Keep encode/decode operation and existing task metadata fields. |
| `TaskSerializer` | `COMPATIBILITY_RETAINED` | Keep deprecated interface in v1.0 with `forRemoval = false`; do not remove. |

Compatibility notes:

- New integrations should prefer `TaskPayloadSerializer` or `TaskPayloadCodec`.
- `TaskSerializer` remains source-compatible but should not receive new capabilities.
- v1.0 does not require new payload schema version, compression, encryption or masking columns.

### 6.6 Starter and Configuration Keys

| Surface | Level | v1.0 Commitment |
| --- | --- | --- |
| `reliable-task-spring-boot-starter` | `STABLE` | Keep Worker-only starter boundary. |
| `reliable-task-admin-spring-boot-starter` | `STABLE` | Keep Admin/Web starter as explicit opt-in. |
| `ReliableTaskProperties` | `STABLE` | Keep existing public configuration keys and defaults unless a later task explicitly documents a breaking change. |
| `additional-spring-configuration-metadata.json` | `STABLE` | Keep metadata aligned with `ReliableTaskProperties` and README tables. |
| `reliable-task.executor.mode` | `STABLE` | Default remains `platform`; `virtual` is opt-in and uses JDK 21 virtual threads with `max-size` as the concurrency limit. |
| `reliable-task.serializer.type` | `RESERVED` | Keep bindable but document that behavior is not wired for switching serializers. |
| `reliable-task.store.table-prefix` | `RESERVED` | Keep bindable but document that current MyBatis tables use fixed names. |
| `reliable-task.admin.port` | `RESERVED` | Keep bindable but document that current Admin APIs use the application server. |
| `reliable-task.admin.context-path` | `RESERVED` | Keep bindable but document current `/api/reliable-task` mapping. |
| `reliable-task-console` | `PREVIEW` | Keep independent from Maven reactor and starter jars. |

Configuration defaults to preserve:

- `reliable-task.enabled=true`
- `reliable-task.worker.enabled=true`
- `reliable-task.worker.batch-size=10`
- `reliable-task.worker.max-batch-size=1000`
- `reliable-task.worker.lock-ttl-seconds=300`
- `reliable-task.recovery.enabled=true`
- `reliable-task.recovery.max-reset-per-scan=100`
- `reliable-task.executor.mode=platform`
- `reliable-task.executor.default-max-size=16`
- `reliable-task.metrics.enabled=false`
- `reliable-task.admin.enabled=false`
- `reliable-task.admin.write-enabled=false`
- `reliable-task.admin.auth.enabled=true`
- `reliable-task.admin.audit.enabled=false`
- `reliable-task.admin.batch.enabled=false`
- `reliable-task.admin.console.payload-plaintext-enabled=false`
- `reliable-task.admin.console.write-confirmation-required=true`

## 7. Schema and Migration Boundary

v1.0 keeps three initialization paths:

- `reliable-task-store/src/main/resources/db/schema.sql`
- `reliable-task-store/src/main/resources/db/migration/V1__init_reliable_task_schema.sql`
- `reliable-task-store/src/main/resources/db/changelog/db.changelog-master.yaml`

Rules:

- A database must choose exactly one path: plain SQL, Flyway or Liquibase.
- If v1.0 does not change schema, the migration guide must explicitly say no schema change is required and cite validation evidence.
- If v1.0 changes schema, the change must synchronize SQL, Flyway, Liquibase, entity/mapper/converter code, migration smoke tests, MySQL profile validation, upgrade guide and release notes.
- Real MySQL release validation must use either Testcontainers `mysql-it` or a disposable local MySQL `mysql-local-it` profile.

## 8. Maven Central Publication Boundary

Core Maven Central candidate artifacts:

- `reliable-task-core`
- `reliable-task-store`
- `reliable-task-executor`
- `reliable-task-admin`
- `reliable-task-spring-boot-starter`
- `reliable-task-admin-spring-boot-starter`

Not core publication artifacts:

- `reliable-task-demo`: local demo/source example; release profile should skip deploy or clearly treat it as non-core.
- `reliable-task-console`: independent frontend preview; not in Maven reactor.

Publication rules:

- Do not commit GPG private keys, passphrases, Central tokens, usernames, passwords, cookies or production endpoints.
- Local dry-run and package/verify checks should work without credentials.
- Real Central publish, staging close/release, tag push or GitHub Release creation requires explicit user authorization.
- Missing credentials or permissions must be recorded as `BLOCKED_ENV` or a release blocker in the appropriate task, never as `PASS`.

## 9. Validation Matrix

| Validation Layer | Command | v1.0 Requirement |
| --- | --- | --- |
| Compile | `cmd.exe /c mvn -B -DskipTests compile` | Must pass before release closure. |
| Default Java tests | `cmd.exe /c mvn -B test` | Must pass before release closure. |
| Store smoke | `cmd.exe /c mvn -B -pl reliable-task-store -am test` | Must pass; includes schema/Flyway/Liquibase smoke coverage. |
| MySQL profile | `cmd.exe /c mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | Testcontainers or local MySQL equivalent must pass before claiming real MySQL readiness. |
| Local MySQL alternative | `cmd.exe /c mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test` | Acceptable alternative when a disposable local MySQL test database is configured. |
| Console install | `cd reliable-task-console && npm ci` | Required for release readiness, but Console does not block Central artifact publication unless release notes claim Console readiness. |
| Console type/lint/unit/build | `npm run typecheck`, `npm run lint`, `npm run test -- --run`, `npm run build` | Required for Console preview release claims. |
| Console smoke | `npm run test:smoke` | Recommended before release; browser/environment issues must be recorded separately. |
| Release profile | `cmd.exe /c mvn -B -Prelease -DskipTests verify` | Must pass after release profile is introduced. |
| Whitespace | `git diff --check` | Must pass for changed text files. |
| Security scan | CodeQL or equivalent plus Maven/Node dependency scanning | Must be configured or documented before release readiness. |
| Sensitive data scan | `rg -n "password|passwd|secret|token|private key|BEGIN .* KEY|jdbc:mysql://|AKIA|ghp_" .` | Must be reviewed manually because examples may produce false positives. |

Validation result labels:

- `PASS`: Command was executed and exited 0.
- `FAIL_CODE`: Command was executed and failure points to repository code, tests, docs, workflow or release config.
- `BLOCKED_ENV`: Failure points to Maven/DNS/Docker/MySQL/Node/npm/Playwright/GPG/Central/GitHub/toolchain environment.
- `NOT_RUN`: Command was not executed; reason must be recorded.

## 10. Acceptance Matrix for v1.0 Scope

| Requirement | Acceptance Evidence |
| --- | --- |
| v1.0 scope is not expanded into v1.x+ features | This document and release notes list non-scope items explicitly. |
| Core API/SPI freeze is reviewable | Inventory above covers submission, handler, status, events, failure classification, Store, payload and starters. |
| Compatibility surfaces are retained | `TaskStore` and `TaskSerializer` are documented as retained in v1.0. |
| at-least-once semantics are consistent | README, upgrade guide, release notes and readiness report must avoid exactly-once claims. |
| Admin safety remains default-safe | Admin API and writes remain disabled by default; write operations require auth, audit and confirmation when enabled. |
| Console stays independent | Console remains outside Maven reactor and starter jars. |
| Schema strategy is explicit | Users can choose exactly one of SQL/Flyway/Liquibase and understand whether v1.0 requires migration. |
| Maven Central readiness is real | Release profile and dry-run are verified without credentials where possible; real publish requires authorization and credentials. |
| Validation results are honest | `BLOCKED_ENV` and `NOT_RUN` are never written as `PASS`. |

## 11. Stop Boundaries

The v1.0 protocol must stop rather than silently continue when:

- A required public API/SPI or schema change is ambiguous and would create irreversible compatibility risk.
- Maven Central publish, tag push or GitHub Release creation is required but explicit user authorization or credentials are missing.
- Docker/Testcontainers and local MySQL are both unavailable for a task that requires real MySQL verification.
- A fix requires committing credentials, production data, private endpoints or destructive Git operations.
- Protocol files disagree and `TASKS.md` cannot identify a single current task.

Ordinary compile, unit test, lint, typecheck, documentation and release-profile failures are not stop conditions by themselves; they must be self-repaired within the current task scope first.
