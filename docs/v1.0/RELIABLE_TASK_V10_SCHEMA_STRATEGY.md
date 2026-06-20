# ReliableTask v1.0 Schema Strategy

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- source_scope: `docs/v1.0/RELIABLE_TASK_V10_SCOPE.md`
- source_test_matrix: `docs/v1.0/RELIABLE_TASK_V10_TEST_MATRIX.md`
- created_at: 2026-06-14 +08:00

## 1. Purpose

This document freezes the v1.0 database initialization and migration policy for ReliableTask.

ReliableTask v1.0 keeps MySQL 8 as the only officially documented database target. It provides three initialization entry points for different user stacks:

- direct SQL: `reliable-task-store/src/main/resources/db/schema.sql`
- Flyway: `reliable-task-store/src/main/resources/db/migration/V1__init_reliable_task_schema.sql`
- Liquibase: `reliable-task-store/src/main/resources/db/changelog/db.changelog-master.yaml`

Users must choose exactly one initialization path for a given database. Running more than one path against the same schema can produce duplicate object or migration metadata conflicts.

## 2. Current Baseline

The v1.0 baseline schema contains six ReliableTask-owned tables:

| Table | Role |
| --- | --- |
| `reliable_task` | Task instance, status, lease, retry and trace state. |
| `reliable_task_log` | Per-attempt execution history. |
| `reliable_task_retry` | Retry plan and result history. |
| `reliable_task_worker` | Worker heartbeat and capacity state. |
| `reliable_task_audit_log` | Admin and system operation audit records. |
| `reliable_task_batch_operation` | Bounded batch operation requests and summaries. |

Key v1.0 invariants:

- `uk_biz_unique_key` remains the task submission idempotency guard.
- `idx_status_next_priority_id` remains the worker polling index.
- `idx_lock_expire` remains the recovery scan index.
- Admin, audit and batch operation indexes remain part of the stable v1.0 schema surface.
- The schema does not rely on `FOR UPDATE SKIP LOCKED`; worker ownership is protected by conditional update, lease and version checks.

## 3. Source Ownership

For v1.0, `schema.sql` is the canonical human-readable baseline.

Flyway V1 is intentionally byte-identical to `schema.sql` so a new Flyway-managed installation creates the same objects as a direct SQL installation. The current SHA256 for both files is:

```text
C35E58347411DBEC91DEBE1776218AA9A6050BCCB64758DC8A3760D8134E9C6C
```

Liquibase currently delegates to the same canonical SQL through:

```yaml
databaseChangeLog:
  - changeSet:
      id: reliable-task-initial-schema
      author: reliable-task
      changes:
        - sqlFile:
            path: db/schema.sql
```

This means Liquibase and direct SQL share the same initial DDL source, while Flyway is kept in sync by byte identity.

## 4. Initialization Rules

| User Setup | Recommended Path | Rule |
| --- | --- | --- |
| No migration framework | Run `schema.sql` once during environment provisioning. | Do not enable Flyway or Liquibase for the same ReliableTask objects. |
| Flyway-managed service | Depend on `reliable-task-store` resources and run Flyway migration `V1__init_reliable_task_schema.sql`. | Do not also execute `schema.sql` manually. |
| Liquibase-managed service | Run `db/changelog/db.changelog-master.yaml`. | Do not also execute `schema.sql` or Flyway. |
| Existing production database | Compare current objects with the v1.0 baseline in a disposable clone before rollout. | Do not run a baseline initializer blindly against production. |

## 5. v0.x to v1.0 Schema Path

No v1.0 DDL change is required relative to the current v0.7.0 baseline files. The upgrade is therefore a schema governance freeze, not a destructive database migration.

Recommended upgrade sequence:

1. Back up the production database or create a disposable clone.
2. Compare existing ReliableTask tables, columns and indexes with `schema.sql`.
3. Confirm that the application uses exactly one initialization mechanism.
4. Upgrade ReliableTask artifacts and configuration.
5. Run the default test suite and at least one real MySQL validation profile before release.
6. Keep old migration metadata intact. If adopting Flyway or Liquibase for a schema that was originally created by manual SQL, baseline or mark the initial change only after DBA review.

Rollback guidance:

- If only Java artifacts changed and no schema drift is detected, rollback can use the previous application artifact version.
- If local teams added private columns or indexes, they remain outside the ReliableTask v1.0 contract and must be validated by the owning team before rollback.
- Never drop ReliableTask tables as part of application rollback unless the database is disposable.

## 6. Evolution Policy After v1.0

For future v1.x releases:

- additive nullable columns and additive indexes are preferred;
- table drops, column drops, incompatible type changes and status code changes require an explicit breaking-change section;
- Flyway changes should use new `V{n}__...` files instead of editing historical migrations after release;
- Liquibase changes should use new changeSets instead of mutating an already released changeSet;
- `schema.sql` may represent the latest full install baseline, but released migration history must remain reproducible.

## 7. Validation Evidence

Automated evidence:

| Evidence | Scope |
| --- | --- |
| `MigrationSmokeTest.schemaSqlAndFlywayV1_areByteIdentical` | Guards byte-level identity between direct SQL and Flyway V1. |
| `MigrationSmokeTest.schemaSql_executesInH2MysqlMode` | Proves direct SQL can create the required schema objects in H2 MySQL mode. |
| `MigrationSmokeTest.flywayMigration_executesInH2MysqlMode` | Proves Flyway V1 can create the required schema objects in H2 MySQL mode. |
| `MigrationSmokeTest.liquibaseChangelog_executesInH2MysqlMode` | Proves Liquibase changelog can create the required schema objects in H2 MySQL mode. |
| `SchemaSqlTest` | Guards schema object and index expectations for the direct SQL baseline. |

Release validation policy:

- `cmd.exe /c mvn -B -pl reliable-task-store -am test` must pass before v1.0 readiness.
- `cmd.exe /c mvn -B -Pmysql-it -pl reliable-task-store -am test` or a disposable local MySQL equivalent must pass before claiming real MySQL schema readiness.
- If Docker/Testcontainers and local MySQL are unavailable, record `BLOCKED_ENV` and do not claim real MySQL validation.

TASK-004 validation snapshot on 2026-06-14 +08:00:

| Command | Result | Notes |
| --- | --- | --- |
| `cmd.exe /c mvn -B -pl reliable-task-store -am test` | `PASS` | 3 related Maven modules built successfully; core 81 tests and store 70 tests passed. `MigrationSmokeTest` now covers direct SQL, Flyway, Liquibase and SQL/Flyway byte identity. |
| `cmd.exe /c mvn -B -Pmysql-it -pl reliable-task-store -am test` | `BLOCKED_ENV` | Testcontainers failed before MySQL tests could run because no valid Docker environment was available. |
| `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store -am -Dtest=MySqlRecoveryIT -Dsurefire.failIfNoSpecifiedTests=false test"` | `PASS` | Targeted recovery validation passed 3 tests against the confirmed local MySQL test configuration. |
| `git diff --check -- docs reliable-task-store/src/main/resources reliable-task-store/src/test/java/com/reliabletask/store/schema/MigrationSmokeTest.java CHANGELOG.md plans/codex_app/reliable-task/v1.0` | `PASS` | Exit code 0; only Windows LF/CRLF working-tree warnings were printed. |

## 8. TASK-004 Decision

The current v1.0 decision is:

- keep the schema unchanged from the current baseline;
- keep `schema.sql` and Flyway V1 synchronized;
- keep Liquibase pointed at the canonical SQL baseline;
- document exactly-one initialization behavior;
- defer broader README and end-to-end upgrade-guide wiring to TASK-005.
