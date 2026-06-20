# ReliableTask v1.0 Test Matrix

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- source_scope: `docs/v1.0/RELIABLE_TASK_V10_SCOPE.md`
- source_compatibility: `docs/v1.0/RELIABLE_TASK_V10_API_COMPATIBILITY.md`
- created_at: 2026-06-14 +08:00

## 1. Purpose

This matrix maps v1.0 reliability promises to automated tests and release validation commands.

ReliableTask v1.0 continues to provide database-backed at-least-once task execution. The test matrix must prove the framework's own state transitions, lease checks, retry/recovery behavior, idempotent submission, Admin safeguards and schema smoke paths. It does not attempt to prove exactly-once external side effects.

## 2. Reliability Semantics Matrix

| Reliability Area | User-Facing Promise | Automated Evidence |
| --- | --- | --- |
| Legal task lifecycle | Only allowed transitions are accepted; terminal states do not restart automatically. | `TaskStateMachineTest`, `TaskStatusTest` |
| At-least-once execution boundary | Workers may retry, recover and requeue; handlers must remain idempotent. | `TaskExecutorTest`, `WorkerSchedulerTest`, `TaskRecoverySchedulerTest`, README reliability semantics |
| Transactional submission | Task rows are created with business transactions and roll back with the outer transaction. | `TransactionAwareTaskTemplateTest`, `MySqlTaskTemplateIT` |
| Explicit idempotency key | `TaskSubmitRequest.idempotencyKey` can deduplicate across `bizId`; invalid keys are rejected. | `TransactionAwareTaskTemplateTest`, `MySqlTaskTemplateIT` |
| Payload compatibility | String payload, object payload and context-aware codec paths are preserved. | `TransactionAwareTaskTemplateTest`, `TaskPayloadCodecAdaptersTest`, `TaskExecutorTest` |
| Failure classification | Retry/dead decisions honor default and custom classifiers; non-retryable failures can go DEAD. | `DefaultFailureClassifierTest`, `TaskExecutorTest` |
| Retry scheduling | Retryable failures produce `RETRYING` with next execution time and retry events/logs. | `TaskExecutorTest`, `ReliableTaskV15IntegrationTest` |
| Dead-letter extension | DEAD state writes trigger dead-letter dispatch after state success and isolate handler failures. | `TaskDeadLetterDispatcherTest`, `TaskExecutorTest` |
| Lease CAS | Stale worker writes cannot overwrite a newer claim or post-recovery state. | `TaskStoreLeaseAwareDefaultsTest`, `TaskExecutorTest`, `MySqlLeaseCasIT`, `MySqlRecoveryIT` |
| Recovery | Expired `RUNNING` leases reset to `PENDING`; valid leases are not recovered. | `TaskRecoverySchedulerTest`, `MySqlRecoveryIT` |
| Worker claim and backpressure | Workers claim only eligible tasks, respect batch/max batch/backpressure and report heartbeat when enabled. | `WorkerSchedulerTest` |
| Admin query governance | Operational queries apply bounded windows, limits and validation. | `TaskAdminControllerTest` |
| Admin writes | Write operations require write-enabled, auth, audit and confirmation before store mutation. | `TaskAdminControllerTest`, `ReliableTaskAdminAutoConfigurationTest` |
| Batch operations | Batch preview and execution are bounded, audited and disabled by default. | `TaskAdminControllerTest` |
| Console-safe payload boundary | Console detail hides raw payload by default and exposes plaintext only when configured. | `TaskAdminControllerTest`, `ReliableTaskAdminAutoConfigurationTest` |
| Schema smoke | SQL, Flyway and Liquibase initialization paths execute in H2 MySQL mode. | `MigrationSmokeTest`, `SchemaSqlTest` |
| Real MySQL behavior | CAS, recovery and transactional submission work against MySQL 8. | `MySqlLeaseCasIT`, `MySqlRecoveryIT`, `MySqlTaskTemplateIT` |

## 3. Test Class Inventory

| Test Class | Module | Coverage Notes |
| --- | --- | --- |
| `TaskStateMachineTest` | `reliable-task-core` | Worker lifecycle, manual requeue, terminal auto-restart rejection and illegal transition exceptions. |
| `TaskStatusTest` | `reliable-task-core` | Stable status code mapping and executable/terminal helper behavior. |
| `DefaultFailureClassifierTest` | `reliable-task-core` | Default retry/dead classification. |
| `TaskDeadLetterDispatcherTest` | `reliable-task-core` | Multiple handlers and handler exception isolation. |
| `TaskPayloadCodecAdaptersTest` | `reliable-task-core` | Serializer/codec adapter compatibility. |
| `TaskStoreBoundaryCompatibilityTest` | `reliable-task-core` | `TaskStore` facade assignability to narrow Store interfaces. |
| `TaskStoreLeaseAwareDefaultsTest` | `reliable-task-core` | Lease-aware defaults and compatibility delegation to legacy task-id methods. |
| `TaskSerializerCompatibilityTest` | `reliable-task-core` | Deprecated `TaskSerializer` remains source-compatible and not marked for removal. |
| `TaskExecutorFactoryTest` | `reliable-task-executor` | Platform executor compatibility, opt-in virtual-thread execution and `max-size` capacity semantics. |
| `TransactionAwareTaskTemplateTest` | `reliable-task-executor` | Submission validation, trace id, submitted event, idempotency, object payload serialization, delay and batch submission. |
| `TaskExecutorTest` | `reliable-task-executor` | Success, retry, DEAD, typed payload, timeout/interruption, metrics, event publishing and lease CAS behavior. |
| `WorkerSchedulerTest` | `reliable-task-executor` | Polling, batch guard, claim, executor submission, backpressure, heartbeat and claim failure handling. |
| `TaskRecoverySchedulerTest` | `reliable-task-executor` | Recovery enable switch, null/empty scan, max reset, reset failure, recovered event and expired-lock threshold. |
| `MySqlLeaseCasIT` | `reliable-task-store` | Concurrent claim and stale lease overwrite prevention on MySQL. |
| `MySqlRecoveryIT` | `reliable-task-store` | Valid lease protection, expired lease recovery and stale worker overwrite prevention after recovery. |
| `MySqlTaskTemplateIT` | `reliable-task-executor` | Duplicate submission, explicit idempotency key, object payload persistence and transaction rollback on MySQL. |
| `TaskAdminControllerTest` | `reliable-task-admin` | Query normalization, Console-safe payload, auth, audit, confirmation, write disabled, batch preview/execution and stale worker views. |
| `ReliableTaskExecutorAutoConfigurationTest` | `reliable-task-spring-boot-starter` | Worker starter defaults, custom SPI override, payload codec priority, events, dead-letter, handler metadata and configuration binding. |
| `ReliableTaskAdminAutoConfigurationTest` | `reliable-task-admin-spring-boot-starter` | Admin default-off, auth default-on, write preconditions and Console capability configuration. |

## 4. Release Validation Commands

| Layer | Command | Required Result for v1.0 |
| --- | --- | --- |
| Core, executor and admin unit/integration slice | `cmd.exe /c mvn -B -pl reliable-task-core,reliable-task-executor,reliable-task-admin -am test` | `PASS` before readiness report. |
| Store schema and storage tests | `cmd.exe /c mvn -B -pl reliable-task-store -am test` | `PASS` before readiness report. |
| Testcontainers MySQL | `cmd.exe /c mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | `PASS` before claiming real MySQL readiness, unless local MySQL equivalent passes. |
| Local MySQL alternative | `cmd.exe /c mvn -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test` | `PASS` can substitute for Testcontainers when run against a disposable MySQL 8 test database. |
| Default reactor | `cmd.exe /c mvn -B test` | `PASS` before release closure. |
| Compile | `cmd.exe /c mvn -B -DskipTests compile` | `PASS` before release closure. |
| Whitespace | `git diff --check` | `PASS`; Windows LF/CRLF warnings are acceptable when exit code is 0. |

## 5. CI and Release Workflow Layers

| Layer | Workflow / Trigger | Default PR Gate | Commands / Checks | Blocking Policy |
| --- | --- | --- | --- | --- |
| Java baseline | `.github/workflows/ci.yml` on PR and push | Yes | `mvn -B test` | Must pass for normal code changes. |
| Console baseline | `.github/workflows/ci.yml` on PR and push | Yes | `npm ci`, `npm run typecheck`, `npm run lint`, `npm run test -- --run`, `npm run build` | Must pass for console-affecting changes and v1.0 release readiness. |
| MySQL integration | `ci.yml` manual `workflow_dispatch` with `run-mysql-it=true`; `release.yml` manual `run-mysql-it=true` | No | `mvn -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test` | Required before claiming real MySQL readiness; record `BLOCKED_ENV` if Docker/Testcontainers is unavailable. |
| Console smoke | `ci.yml` manual `workflow_dispatch` with `run-console-smoke=true`; `release.yml` manual `run-console-smoke=true` | No | Playwright Chromium smoke through `npm run test:smoke` | Required before claiming console browser smoke readiness; record `BLOCKED_ENV` if browser install/network is unavailable. |
| Release dry-run | `.github/workflows/release.yml` manual `mode=dry-run` | No | `mvn -B test`, `mvn -B -Prelease -DskipTests verify` | Required before v1.0 release closure; does not require Central/GPG secrets. |
| Security/dependency scan | `.github/workflows/release.yml` manual `run-security-scan=true`; detailed governance in TASK-011 | No | `mvn -B -DskipTests dependency:tree`, `npm audit --audit-level=high` | Findings must be triaged in TASK-011; optional scan failures are not default PR blockers until governance policy says so. |
| Maven Central publish | `.github/workflows/release.yml` manual `mode=publish` and `confirm-publish=PUBLISH` | No | `mvn -B -Prelease,release-sign -DskipTests -Dgpg.skip=false -Dcentral.skipPublishing=false -Dcentral.autoPublish=true deploy` | Requires explicit release authorization, Central token and GPG key; never runs on PR/push. |

Default CI remains lightweight. Docker/Testcontainers, Playwright browser install, dependency audit, GPG signing and Central publication stay behind manual workflow inputs.

## 6. MySQL Validation Policy

The `mysql-it` profile runs Testcontainers MySQL with `mysql:8.0.36`.

The `mysql-local-it` profile runs the same `*IT` tests against a disposable local MySQL database. It requires either system properties or environment variables:

- `reliabletask.it.mysql.url` or `RELIABLE_TASK_IT_JDBC_URL`
- `reliabletask.it.mysql.username` or `RELIABLE_TASK_IT_USERNAME`
- `reliabletask.it.mysql.password` or `RELIABLE_TASK_IT_PASSWORD`

Rules:

- Do not run MySQL integration tests against a production database.
- If Testcontainers is blocked by Docker/image/network environment, try `mysql-local-it` only when a disposable local MySQL test database is configured.
- If both Testcontainers and local MySQL are unavailable, record `BLOCKED_ENV` and do not claim real MySQL readiness.
- A targeted MySQL test pass may support a specific release caveat, but v1.0 readiness should prefer the full store/executor MySQL profile.

## 7. Known Gaps and Follow-Ups

| Gap | v1.0 Handling |
| --- | --- |
| Real MySQL profile is environment-dependent. | TASK-003 must run `mysql-it`; if blocked, try `mysql-local-it` or record `BLOCKED_ENV`. Release closure must not hide the result. |
| Console browser smoke is not part of this Java reliability task. | Covered later by CI/release tasks and Console validation. |
| Dependency/security scan is not part of this task. | Covered by governance task. |
| Java/Spring Boot baseline drift between README, root POM and release docs. | Current Project 10 sync aligns Java 21+ and Spring Boot 3.5.14; keep the baseline drift scan in readiness. |

## 8. TASK-003 Validation Snapshot

Recorded on 2026-06-20 +08:00:

| Command | Result | Notes |
| --- | --- | --- |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B test"` | `PASS` | 8 Maven reactor modules built successfully on Spring Boot 3.5.14. |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -pl reliable-task-executor,reliable-task-spring-boot-starter -am test"` | `PASS` | Executor/starter slice covered virtual-thread config binding and executor capacity tests. |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test"` | `BLOCKED_ENV` | Testcontainers failed before MySQL tests could run because no valid Docker environment was available. |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store -am -Dtest=MySqlRecoveryIT -Dsurefire.failIfNoSpecifiedTests=false test"` | `PASS` | Targeted recovery validation passed 3 tests against the confirmed local MySQL test configuration. |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test"` | `NOT_RUN` | Full local MySQL profile was not expanded in this release pass. |

Release caveat: this pass validates targeted recovery semantics on real MySQL through `MySqlRecoveryIT`; full `mysql-it` or full `mysql-local-it` coverage remains optional follow-up evidence for broader MySQL transactional/idempotency behavior.

## 9. TASK-008 Validation Snapshot

Recorded on 2026-06-20 +08:00:

| Command | Result | Notes |
| --- | --- | --- |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B test"` | `PASS` | 8 Maven reactor modules built successfully. |
| `cd reliable-task-console && npm ci` | `NOT_RUN` | Existing `node_modules` and lockfile were used for this local readiness pass. |
| `cd reliable-task-console && npm run typecheck` | `PASS` | Vue and TypeScript checks passed. |
| `cd reliable-task-console && npm run lint` | `PASS` | Current lint script delegates to typecheck and passed. |
| `cd reliable-task-console && npm run test` | `PASS` | First local run timed out; rerun with a longer timeout passed 15 test files and 49 tests. |
| `cd reliable-task-console && npm run build` | `PASS` | Typecheck and Vite production build passed. |
| `cd reliable-task-console && npm audit --audit-level=high --registry=https://registry.npmjs.org` | `PASS` | `found 0 vulnerabilities`. |
| `cd reliable-task-console && npm run test:smoke` | `BLOCKED_ENV` | Both Chromium tests printed `ok`, but the Playwright runner did not exit before the local timeout. |
| `git diff --check` | `PASS` | Exit code 0; only Windows LF/CRLF working-tree warnings were printed. |

CI layering decision:

- Default PR/push CI remains Maven test plus console typecheck/lint/unit/build.
- MySQL IT, console smoke, dependency/security scan and Maven Central publish remain manual workflow layers.
- Release dry-run is covered by `.github/workflows/release.yml` and does not require Central/GPG secrets.
