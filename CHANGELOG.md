# Changelog

All notable changes to this project will be documented in this file.

This project follows Semantic Versioning.

## [Unreleased]

No changes yet.

## [0.7.0] - 2026-06-14

### Added

- Add the v0.7 Web console scope and acceptance baseline in `docs/review/RELIABLE_TASK_V07_SCOPE.md`.
- Add console capabilities and console-safe task detail APIs for the v0.7 preview.
- Add the standalone `reliable-task-console` Vue/Vite preview with dashboard, task list/detail, payload-safe detail panels, worker views, audit logs, read-only troubleshooting paths, guarded single-task writes, guarded batch preview/execute UI, and Playwright smoke coverage.
- Add the v0.7 readiness report in `docs/review/RELIABLE_TASK_V07_READINESS_REPORT.md`.

### Changed

- Tighten Admin write APIs so enabled writes still require authorization, audit logging, and `X-Confirm-Operation: true` by default before mutating task state.
- Document local demo + console startup, independent static deployment, Vite proxy settings, payload safety, and write-operation prerequisites.
- Split CI into Maven, console typecheck/lint/unit/build, optional console smoke, and optional MySQL integration layers.
- Change project release version to `0.7.0`.

### Planned

- Plan v0.7.x as an independent Web console preview with Vue 3, TypeScript, Vite, and Ant Design Vue.
- Prioritize read-only troubleshooting before write operations: dashboard, task list, console-safe task detail, logs, timeline, Worker views, audit logs, failure aggregation, and common empty/error states.
- Add backend console capabilities and payload-safe detail contracts before any UI can expose payload fields.
- Require auth, audit, confirmation headers, operator identity, trace ids, and batch limits before console write operations become available.
- Keep v1.0 API freeze, Maven Central release, complex multi-tenant platform, complex reporting, mobile-first redesign, and new storage/MQ/workflow backends out of v0.7 scope.

## [0.6.0] - 2026-06-13

### Added

- Add narrow Store SPI interfaces: `TaskCommandStore`, `TaskQueryStore`, and `TaskOperationsStore`, while keeping `TaskStore` as a compatibility facade.
- Add context-aware `TaskPayloadCodec` with adapters for existing `TaskPayloadSerializer` beans.
- Add ordered `TaskInterceptor` chain wiring and default trace cleanup behavior.
- Add `TaskTraceIdGenerator`, `TaskNameResolver`, and `TaskHandlerMetadata` extension points.
- Add Flyway and Liquibase initial schema entry points for ReliableTask table initialization, backed by H2 MySQL-mode smoke tests.
- Add `reliable-task-admin-spring-boot-starter` for explicit Admin REST auto-configuration and Web dependencies.

### Changed

- Split Admin REST auto-configuration into `reliable-task-admin-spring-boot-starter`; `reliable-task-spring-boot-starter` is now worker-only and no longer brings Admin/Web dependencies by default.
- Rename the retry-table task-id index to `idx_retry_task_id` so the initial schema can be validated consistently in H2 MySQL mode while preserving indexed columns.
- Change project release version to `0.6.0`.

### Planned

- Keep v0.7/v1.0 items out of v0.6 scope, including Web console, multi-store or MQ backends, Redis locks, workflow/DAG orchestration, complex multi-tenant platforms, Maven Central publication, and v1.0 API freeze.

## [0.5.0] - 2026-06-13

### Added

- Define the v0.5 production operations scope and acceptance baseline.
- Add bounded Admin read APIs for recent failed executions and slow execution records.
- Add bounded Admin failure-top aggregation API by task type and error code.
- Add lightweight Admin task lifecycle timeline API based on task rows, execution logs, and audit logs.
- Add `TaskDeadLetterHandler` SPI, no-op default handler, and dead-letter dispatcher auto-configuration.
- Wire dead-letter dispatch into no-handler and RetryEngine DEAD paths after successful DEAD state writes.
- Add production monitoring, Prometheus alert examples, and incident runbook documentation under `docs/operations`.
- Record v0.5 readiness findings for release preparation.

### Changed

- Align Admin configuration defaults across metadata, README, demo documentation, and starter tests: Admin REST APIs are disabled by default, Admin write operations remain disabled, and Admin authorization checks are enabled by default when Admin is explicitly registered.
- Clarify reserved configuration keys for serializer type, store table prefix, Admin port, and Admin context path.
- Add Admin operational query guard configuration for default time windows, maximum windows, default limits, maximum limits, and slow-task thresholds without changing existing `/tasks` and `/audit-logs` list behavior.
- Expand production adoption, demo, and release-process documentation for v0.5 operational queries, dead-letter SPI, runbooks, Admin safety, payload sensitivity, alert thresholds, MySQL validation profiles, and recovery readiness.
- Change project release version to `0.5.0`.

### Planned

- Plan v0.5.x production operations work for Admin query governance, recent failure and slow-task queries, failure-top aggregation, task timeline, dead-letter SPI, monitoring templates, runbooks, production checklist updates, and configuration metadata consistency.
- Keep v0.6/v0.7 items out of v0.5 scope, including `TaskStore` splitting, Web console, multi-store backends, MQ/Kafka backends, Redis locks, and workflow orchestration.

## [0.3.0] - 2026-06-12

### Added

- Add the v0.3.x MVP scope and verification matrix in `docs/review/RELIABLE_TASK_V03_SCOPE.md`.
- Add an opt-in `mysql-it` Maven profile with Testcontainers MySQL smoke test infrastructure for store integration testing.
- Add an opt-in `mysql-local-it` Maven profile so the same MySQL integration tests can run against a dedicated local MySQL 8 database.
- Add reliability semantics, handler idempotency guidance, and a production adoption checklist to README and demo documentation.
- Add explicit `TaskSubmitRequest.idempotencyKey` support for user-controlled submission deduplication keys.
- Add registered `RetryStrategyType.CUSTOM` support and configurable exponential retry jitter/min/max delay settings.
- Add minimal `FailureClassifier` SPI support for overriding retry/dead decisions.
- Add lightweight `TaskEventListener` events for task submit, start, success, retry, dead, cancel, requeue, and recovery state changes.
- Add low-cardinality Micrometer defaults, queue backlog/oldest-pending gauges, stats gauge caching, and recovery event counters.
- Add layered CI and release validation guidance for default tests versus opt-in real MySQL integration tests.
- Add the v0.3/v0.4 readiness report in `docs/review/RELIABLE_TASK_V03_V04_READINESS_REPORT.md`.
- Add `TaskStateMachine` to centralize legal task status transitions.
- Add `TaskExceptionFormatter` SPI and default compressed stack diagnostic formatter.
- Add configurable worker claim lock TTL through `reliable-task.worker.lock-ttl-seconds`.
- Add execution log trace fields: `attempt_no`, `status_before`, and `status_after`.
- Add real Admin switches for audit-log endpoints and batch-operation endpoints.

### Changed

- Change project release version to `0.3.0`.
- Use `reliable-task.recovery.timeout-seconds` when building the timeout recovery threshold.
- Enforce `TaskHandler.maxConcurrency()` with handler-level semaphores.
- Cancel timed-out execution futures with interruption before scheduling retry/dead handling.
- Disable Admin write APIs by default through `reliable-task.admin.write-enabled=false`; demos must opt in explicitly.

### Fixed

- Remove hard-coded initial task claim lock TTL.
- Preserve execution attempt and before/after status values in task logs.
- Avoid scheduler errors when custom stores return null task lists.
- Use a unique create-time index name on `reliable_task_log` for H2/MySQL-mode DDL validation.

## [0.1.0] - 2026-05-05

### Added

- Initial public release of ReliableTask.
- Add core task models, lifecycle states, retry strategies, idempotency SPI, and task submission APIs.
- Add MyBatis-Plus and MySQL task store with schema, task logs, worker heartbeat, audit log, and batch operation tables.
- Add worker scheduling, task claiming, asynchronous execution, retry handling, timeout recovery, lease renewal, metrics hooks, and thread pool isolation.
- Add Spring Boot starter auto-configuration and configuration metadata.
- Add Admin APIs for task query, details, logs, statistics, manual retry, requeue, cancel, payload update, worker views, audit logs, and limited batch operations.
- Add demo application for transactional task submission, retries, duplicate submission handling, object payloads, and local curl scripts.
- Add Apache-2.0 license, README, contribution and security docs, issue and pull request templates, and Maven CI workflow.

### Security

- Document that Admin APIs require authentication, authorization, audit logging, and network access controls before production use.
- Keep real local configuration out of version control and use placeholder values in example configuration.

[Unreleased]: https://github.com/naruto863/reliable-task/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/naruto863/reliable-task/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/naruto863/reliable-task/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/naruto863/reliable-task/compare/v0.3.0...v0.5.0
[0.3.0]: https://github.com/naruto863/reliable-task/compare/v0.1.0...v0.3.0
[0.1.0]: https://github.com/naruto863/reliable-task/releases/tag/v0.1.0
