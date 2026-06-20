# ReliableTask v1.0 Example Matrix

- target_version: `v1.0.0`
- baseline_version: `0.7.0`
- source_demo: `reliable-task-demo/README.md`
- source_console: `reliable-task-console/README.md`
- source_operations: `docs/operations`
- created_at: 2026-06-14 +08:00

## 1. Purpose

This matrix maps common v1.0 adoption and troubleshooting scenarios to runnable examples, documentation and safety notes.

The examples are intentionally local or placeholder-based. Do not replace them with real credentials, bearer tokens, production URLs, real user data, raw personal data, or sensitive payloads.

## 2. Scenario Matrix

| Scenario | Primary Example | Where to Run or Read | Expected Result | Safety Notes |
| --- | --- | --- | --- | --- |
| Successful task submission and execution | Submit a demo order and let the Worker execute `CREATE_SHIPMENT`. | `reliable-task-demo/README.md` command `POST /demo/order?orderNo=ORD-001&buyerId=USER-123`. | Task is created, claimed and eventually reaches `SUCCESS`. | `ORD-001` and `USER-123` are placeholders; use stable non-sensitive business identifiers. |
| Retryable failure | Demo retry-once path and retry metrics. | `reliable-task-demo` plus Admin task list/logs and `reliable_task_retry_total`. | Task first moves through retry behavior, then succeeds or remains inspectable. | Retry does not imply exactly-once side effects; handlers must be idempotent. |
| Non-retryable failure | Submit the non-retryable demo order. | `POST /demo/order/non-retryable?orderNo=ORD-BAD-001&buyerId=USER-123`. | Task moves to `DEAD` without retrying. | Classify permanent business errors carefully; do not bulk requeue non-idempotent failures. |
| Explicit idempotency key | Submit duplicate demo orders. | `POST /demo/order/duplicate?orderNo=ORD-DUP-001&buyerId=USER-123`. | Duplicate submission returns the same task id / effective `bizUniqueKey`. | Do not put phone numbers, IDs, tokens, credentials or raw payload text in `idempotencyKey`. |
| Object payload | Submit object payload demo order. | `POST /demo/order/object-payload?orderNo=ORD-OBJ-001&buyerId=USER-123&address=Shanghai`. | Payload is serialized through the configured payload serializer/codec path. | Keep payload examples synthetic; production payload visibility is controlled by Console-safe settings. |
| Dead-letter handling | Register a `TaskDeadLetterHandler` bean. | Root README "Dead Letter Handler SPI" section. | Handler is called after a task is successfully marked `DEAD`. | Dead-letter handler failures must not hide the original task state; use it for notification/archive/compensation. |
| Admin read troubleshooting | Query task stats, details, logs, recent failures, slow tasks and failure top. | `reliable-task-demo/README.md`, `docs/operations/reliable-task-runbook.md`. | Operator can diagnose backlog, dead tasks and retry storms without direct database access. | Admin APIs must stay behind internal auth, authorization, audit and network controls. |
| Admin write safeguards | Retry, cancel, requeue or update payload only when backend capabilities allow it. | `reliable-task-console/README.md`, root README Admin safety notes. | Console write buttons remain disabled until write/auth/audit/confirmation requirements are met. | Do not expose write APIs publicly; require `X-Confirm-Operation: true` and auditable operator identity. |
| Console read-only troubleshooting | Run local console against demo or Playwright smoke with mocked Admin API. | `reliable-task-console/README.md`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`. | Dashboard, task list/detail, workers and audit views render safe troubleshooting states. | Console is independent static frontend; deploy behind the same protected operations boundary as Admin APIs. |
| Monitoring metrics | Inspect Micrometer metrics and PromQL examples. | `docs/operations/reliable-task-monitoring.md`, `docs/operations/prometheus-alerts-example.yml`. | Operators can monitor backlog, retry rate, dead count, recovery and worker capacity. | Thresholds are examples only; tune per service and avoid high-cardinality worker tags by default. |
| Migration scripts | Choose exactly one schema initialization path. | `docs/migration/v1.0-upgrade-guide.md`, `docs/v1.0/RELIABLE_TASK_V10_SCHEMA_STRATEGY.md`. | Direct SQL, Flyway or Liquibase initializes the same v1.0 baseline. | Never run multiple initialization paths on the same database; validate production changes in a disposable clone first. |

## 3. Local Demo Quick Path

Start the demo backend:

```bash
mvn -pl reliable-task-demo -am spring-boot:run
```

Start the console:

```bash
cd reliable-task-console
npm install
npm run dev
```

Use local example values only:

```bash
curl -X POST "http://localhost:8080/demo/order?orderNo=ORD-001&buyerId=USER-123"
curl -X POST "http://localhost:8080/demo/order/non-retryable?orderNo=ORD-BAD-001&buyerId=USER-123"
curl -X POST "http://localhost:8080/demo/order/duplicate?orderNo=ORD-DUP-001&buyerId=USER-123"
curl -H "X-Operator: admin" "http://localhost:8080/api/reliable-task/tasks/stats"
```

The local `X-Operator: admin` header is a demo placeholder, not a production authentication model.

## 4. Production Example Rules

- Use `example.invalid`, local hostnames or synthetic identifiers in shared docs.
- Keep real database URLs, usernames, passwords, bearer tokens, cookies and private hostnames out of examples.
- Keep Admin write examples disabled unless the surrounding environment has authentication, authorization, audit logging, confirmation headers and network controls.
- Treat every retry, recovery and requeue example as at-least-once. Business handlers must own external side-effect idempotency.
- Do not claim Maven Central, MySQL, Playwright or security validation unless the corresponding command actually passed.
