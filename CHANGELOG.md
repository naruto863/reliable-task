# Changelog

All notable changes to this project will be documented in this file.

This project follows Semantic Versioning.

## [Unreleased]

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
