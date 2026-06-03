# Changelog

All notable changes to this project will be documented in this file.

This project follows Semantic Versioning.

## [Unreleased]

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

- Change project development version to `0.2.0-SNAPSHOT`.
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

[Unreleased]: https://github.com/naruto863/reliable-task/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/naruto863/reliable-task/releases/tag/v0.1.0
