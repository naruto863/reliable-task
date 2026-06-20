# ReliableTask v1.0 Readiness Report

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- report_date: 2026-06-20
- protocol: `plans/codex_app/reliable-task/v1.0`
- status: v1.0.0 source/tag release authorized; local release gates passed, Maven Central remains credentials-gated

## Executive Summary

ReliableTask is ready to enter the v1.0 release closure task with the core API/SPI, schema strategy, Maven Central dry-run path, documentation, example matrix, CI/release workflow and open-source governance prepared.

This report records the local release gates for the authorized v1.0.0 source/tag release. Maven Central publication and Central dependency verification remain separate credentials-gated steps that require Central/GPG secrets.

Project 10 updates include Spring Boot dependency management `3.5.14`, Java 21-only runtime documentation, opt-in virtual-thread executor mode, tracked public v1.0 documentation under `docs/v1.0/`, Console/Admin roadmap documentation and community onboarding.

Current readiness result:

- `PASS`: default Java test suite on Spring Boot 3.5.14.
- `PASS`: release profile dry-run verify; existing Javadoc warnings remain non-fatal.
- `PASS`: Console typecheck, lint, unit tests, build and npm audit.
- `PASS`: targeted local MySQL recovery integration, `MySqlRecoveryIT` with `mysql-local-it`, 3 tests.
- `PASS_WITH_FALSE_POSITIVES`: sensitive information scan.
- `BLOCKED_ENV`: Console Playwright smoke command printed both tests as `ok` but the runner process did not exit before the local timeout, so it is not counted as `PASS`.
- `BLOCKED_ENV`: Testcontainers MySQL validation in this local environment, because Docker/Testcontainers is unavailable.
- `NOT_RUN`: full MySQL profile; this release pass intentionally ran the targeted `MySqlRecoveryIT` requested for release validation.
- `NOT_RUN`: GitHub-side CodeQL and Dependabot results, because the new workflow/config must be committed and pushed before GitHub can run them.

## Scope and Compatibility

v1.0 is a stability and publication release, not a feature expansion. The frozen surfaces are documented in:

- `docs/v1.0/RELIABLE_TASK_V10_SCOPE.md`
- `docs/v1.0/RELIABLE_TASK_V10_API_COMPATIBILITY.md`

Stable or compatibility-retained surfaces include:

- submission and handler API: `TaskTemplate`, `TaskSubmitRequest`, `TaskSubmitResult`, `TaskHandler`, annotations, retry strategy and task status;
- reliability semantics: `TaskStateMachine`, leases, at-least-once execution and Handler idempotency responsibility;
- event, failure, diagnostic and dead-letter SPI;
- Store SPI: `TaskCommandStore`, `TaskQueryStore`, `TaskOperationsStore`, with `TaskStore` retained as the compatibility facade;
- payload SPI: `TaskPayloadSerializer`, `TaskPayloadCodec`, `TaskPayloadCodecContext`, with deprecated `TaskSerializer` retained in v1.0;
- Worker-only starter and explicit Admin/Web starter boundaries;
- documented reserved compatibility properties.

No mandatory source-breaking or schema-breaking change has been identified for a normal `0.7.0` to `1.0.0` upgrade in the current protocol. Java 21 is the documented build/runtime baseline, Spring Boot dependency management is `3.5.14`, and `reliable-task.executor.mode=virtual` remains opt-in with `platform` as the default.

## Schema and Migration

Schema strategy is documented in:

- `docs/v1.0/RELIABLE_TASK_V10_SCHEMA_STRATEGY.md`
- `docs/migration/v1.0-upgrade-guide.md`

v1.0 keeps three initialization paths:

- plain SQL: `reliable-task-store/src/main/resources/db/schema.sql`
- Flyway: `reliable-task-store/src/main/resources/db/migration/V1__init_reliable_task_schema.sql`
- Liquibase: `reliable-task-store/src/main/resources/db/changelog/db.changelog-master.yaml`

Users must choose exactly one initialization path per database. The current protocol found no required v1.0 schema change. H2 MySQL-mode schema, Flyway and Liquibase smoke checks pass in the default Maven test suite.

Targeted real MySQL recovery validation passed in this release pass. `MySqlRecoveryIT` ran through the `mysql-local-it` profile against the confirmed local MySQL test configuration and passed 3 tests. Testcontainers validation remains blocked in this environment because Docker is not available, and the full MySQL profile was not expanded beyond the requested targeted recovery test.

## Maven Central and Release Workflow

Maven Central readiness work is prepared but not published:

- root POM metadata includes license, SCM, developer and issue management information;
- release profile generates sources and javadoc jars;
- Central Publisher Portal plugin is configured with dry-run-safe defaults;
- signing is isolated behind the explicit `release-sign` profile;
- demo is excluded from core Central publication;
- release workflow requires manual `workflow_dispatch`, guarded `mode=publish`, and `confirm-publish=PUBLISH`.

Validated local dry-run:

- `PASS`: `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Prelease -DskipTests verify"`, 8 reactor modules `BUILD SUCCESS`; existing Javadoc warnings remain non-fatal.

Pending Maven Central publish work:

- configure Central/GPG/GitHub credentials through secrets or local settings;
- publish artifacts;
- verify starter consumption from Maven Central or staging.

## Documentation and Examples

User-facing v1.0 documentation has been synchronized across:

- `README.md`
- `README.zh-CN.md`
- `reliable-task-demo/README.md`
- `reliable-task-console/README.md`
- `docs/release-process.md`
- `docs/migration/v1.0-upgrade-guide.md`
- operations monitoring and runbook docs
- `docs/v1.0/RELIABLE_TASK_V10_EXAMPLE_MATRIX.md`
- `docs/console-admin-roadmap.md`
- `docs/community-onboarding.md`
- `CHANGELOG.md`

The documentation consistently states:

- ReliableTask provides at-least-once execution, not exactly-once external side effects.
- Handler implementations must make external side effects idempotent.
- Admin APIs are internal operations surfaces and write operations remain guarded.
- Console is an independent preview frontend and is not a core Maven artifact.
- Console/Admin roadmap keeps preview scope, Admin safety boundaries and non-goals explicit.
- Community onboarding and issue/PR templates route bugs, feature requests, security reports and contributions safely.
- Maven Central is the v1.0 target install path only after release closure confirms publication.

## Open Source Governance

Governance report:

- `docs/open-source-check-report.md`

Prepared governance controls:

- `LICENSE` is Apache-2.0 with ReliableTask contributors as the copyright holder.
- `SECURITY.md` defines supported version policy, private disclosure route and release security checks.
- `CONTRIBUTING.md` documents Java, Console and dependency validation.
- `docs/community-onboarding.md` documents issue routing, first-contribution guidance and validation result labels.
- `.github/workflows/codeql.yml` configures CodeQL for Java and JavaScript/TypeScript.
- `.github/dependabot.yml` tracks Maven, npm and GitHub Actions updates.
- `.github/workflows/release.yml` provides optional release-time dependency inventory and npm audit.

Local security validation:

- `PASS`: `cmd.exe /c mvn -B -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree`.
- `PASS`: `cd reliable-task-console && npm audit --audit-level=high --registry=https://registry.npmjs.org`, `found 0 vulnerabilities`.
- `PASS_WITH_FALSE_POSITIVES`: sensitive scan found only examples, placeholder config, test redaction fixtures, package names and security guidance text.

GitHub-side CodeQL and Dependabot results are `NOT_RUN` locally. They require the new workflow/config files to be committed and pushed.

## Validation Snapshot

| Layer | Command | Result | Notes |
| --- | --- | --- | --- |
| Java default tests | `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B test"` | `PASS` | 8 reactor modules `BUILD SUCCESS`; H2 schema, Flyway and Liquibase smoke included. |
| Release dry-run | `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Prelease -DskipTests verify"` | `PASS` | 8 reactor modules `BUILD SUCCESS`; sources/javadoc jars generated, no Central upload; existing Javadoc warnings remain non-fatal. |
| Testcontainers MySQL | `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-it -pl reliable-task-store,reliable-task-executor -am test"` | `BLOCKED_ENV` | Testcontainers could not find a valid Docker environment. |
| Targeted local MySQL recovery | `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store -am -Dtest=MySqlRecoveryIT -Dsurefire.failIfNoSpecifiedTests=false test"` | `PASS` | `MySqlRecoveryIT` passed 3 tests against the confirmed local MySQL test configuration. |
| Full local MySQL profile | `cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -Pmysql-local-it -pl reliable-task-store,reliable-task-executor -am test"` | `NOT_RUN` | Release pass intentionally used the requested targeted recovery IT instead of expanding to the full profile. |
| Console install | `cd reliable-task-console && npm ci` | `NOT_RUN` | Existing `node_modules` and lockfile were used for this local readiness pass. |
| Console typecheck | `cd reliable-task-console && npm run typecheck` | `PASS` | `vue-tsc` and node tsconfig passed. |
| Console lint | `cd reliable-task-console && npm run lint` | `PASS` | Current lint script delegates to typecheck. |
| Console unit tests | `cd reliable-task-console && npm run test` | `PASS` | First run timed out locally; rerun with a longer timeout passed 15 files and 49 tests. |
| Console build | `cd reliable-task-console && npm run build` | `PASS` | Typecheck plus Vite 8 production build passed; plugin timing advisory only. |
| Console smoke | `cd reliable-task-console && npm run test:smoke` | `BLOCKED_ENV` | Both Chromium tests printed `ok`, but the Playwright runner did not exit before 180s or 300s local timeouts. |
| npm audit | `cd reliable-task-console && npm audit --audit-level=high --registry=https://registry.npmjs.org` | `PASS` | `found 0 vulnerabilities`. |
| Public v1.0 link scan | `rg -n "docs/review/RELIABLE_TASK_V10" ... -g "!docs/review/**"` | `PASS` | No public document links point at ignored `docs/review/RELIABLE_TASK_V10_*` files. |
| Baseline drift scan | `rg -n "3\.2\.5|Java 17|Java-17|Java \| 17|README currently advertises" ...` | `PASS` | Only CHANGELOG intentionally mentions the old Spring Boot version as the upgrade source. |
| Sensitive scan | `rg -n "password|passwd|secret|token|private key|BEGIN .* KEY|jdbc:mysql://|AKIA|ghp_" ...` | `PASS_WITH_FALSE_POSITIVES` | Hits are example JDBC URLs, documented secret names, redaction tests, package names and security guidance; no real secret identified. |
| Diff hygiene | `git diff --check` | `PASS` | No whitespace errors; Git reports LF/CRLF working-copy warnings only. |

## Release Caveats

Remaining caveats after the v1.0.0 source/tag release gates:

- Console smoke should be rerun in an environment where the Playwright runner exits cleanly after the two Chromium tests pass.
- CodeQL should run on GitHub after the workflow is committed.
- Dependabot/security alerts should be checked in the GitHub repository after the config is committed.
- Maven Central/GPG credentials must be configured only through secrets or local settings before Central publication.
- Full MySQL profile can be run later if broader transactional/idempotency MySQL coverage is required beyond the targeted release validation.

## Readiness Decision

The v1.0.0 source/tag release gates are complete for the authorized local release flow. Maven Central publication remains separate and must either complete through the guarded publish workflow or record a hard blocker for missing Central/GPG/GitHub credentials.
