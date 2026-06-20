# ReliableTask Production Runbook

This runbook provides database-free troubleshooting paths for ReliableTask production incidents.
Use the Admin APIs and metrics below before reaching for direct database inspection.

For a scenario-to-example index across demo, console, monitoring and migration docs, see [ReliableTask v1.0 Example Matrix](../review/RELIABLE_TASK_V10_EXAMPLE_MATRIX.md).

## Before You Start

- Confirm the service exposes Micrometer metrics and `reliable-task.metrics.enabled=true`.
- Confirm Admin APIs are enabled only on the internal operations path with production authentication and authorization.
- Keep `reliable-task.admin.write-enabled=false` until a change is explicitly approved and audited.
- Use `X-Operator` and trace headers required by the host service so audit records can identify the operator.
- Treat every threshold in sample alerts as a placeholder, not as a universal production standard.

Set a local shell variable for examples:

```bash
export ADMIN_BASE_URL="https://reliable-task-admin.example.invalid"
```

## Quick Checks

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/stats" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/workers" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/workers/stale" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/failure-top?groupBy=taskType,errorCode&limit=20" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/recent-failures?limit=20" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/slow?durationMsGte=30000&limit=20" -H "X-Operator: oncall"
```

## Backlog Growth

Primary signals:

- `sum(reliable_task_backlog_total)` is rising.
- `max(reliable_task_oldest_pending_age_seconds)` is rising.
- `sum(reliable_task_worker_available_capacity)` is low or zero.

Diagnosis steps:

1. Check `GET /tasks/stats` to confirm pending, running, and dead totals.
2. Check `GET /workers` and `GET /workers/stale` to verify active workers and heartbeat freshness.
3. Check `reliable_task_worker_available_capacity`. If capacity is zero, investigate executor saturation, handler latency, or stuck external calls.
4. Query slow tasks: `GET /tasks/slow?durationMsGte=30000&limit=20`.
5. Query recent failures and failure top to see whether the backlog is caused by repeated retries.
6. Inspect application logs for rejected submissions, handler timeouts, downstream HTTP/RPC latency, or pool exhaustion.

Mitigation options:

- Scale worker instances or increase executor capacity only after confirming database and downstream capacity.
- Temporarily reduce producers or pause low-priority task creation if the backlog threatens core traffic.
- Tune `worker.batch-size`, `worker.max-batch-size`, and handler timeouts based on measured throughput.
- Fix slow or blocking handlers before increasing retry counts.

Resolution evidence:

- Backlog stops increasing.
- Oldest pending age returns to the service's expected range.
- Worker capacity remains positive during normal traffic.

## Dead Task Spike

Primary signals:

- `sum by (task_type) (rate(reliable_task_failed_total{status="DEAD"}[5m]))` increases.
- `sum(reliable_task_dead_total)` increases.
- Dead-letter handler logs or notifications increase.

Diagnosis steps:

1. Query recent failures: `GET /tasks/recent-failures?limit=50`.
2. Query failure top: `GET /tasks/failure-top?groupBy=taskType,errorCode&limit=20`.
3. For representative task IDs, query details, logs, and timeline:

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}/logs" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}/timeline" -H "X-Operator: oncall"
```

4. Confirm whether `FailureClassifier` intentionally sends the error to `DEAD`.
5. Check whether the dead-letter handler delivered external notifications or archival records.

Mitigation options:

- Fix the handler or downstream dependency before requeueing.
- Requeue or retry only the affected task type or error code after root cause is understood.
- Keep Admin write operations behind `write-enabled`, auth, audit, and network controls.
- Do not bulk requeue tasks with non-idempotent side effects until the business owner confirms the replay plan.

Resolution evidence:

- Dead rate returns to baseline.
- New recent failures stop showing the same error code.
- Requeued tasks complete successfully or move to an expected non-retryable terminal state.

## Retry Storm

Primary signals:

- `sum by (task_type) (rate(reliable_task_retry_total[5m]))` spikes.
- `sum by (task_type, status) (rate(reliable_task_failed_total[5m]))` spikes with retrying status.
- Backlog and oldest pending age may rise at the same time.

Diagnosis steps:

1. Query failure top with `groupBy=taskType,errorCode`.
2. Query recent failures filtered by the top task type or error code.
3. Check external dependency status for the failing task type.
4. Review retry configuration: `@TaskRetryable`, jitter, min/max delay, and `FailureClassifier`.
5. Confirm whether the same business idempotency key is being submitted repeatedly.

Mitigation options:

- Classify permanent errors as non-retryable after business confirmation.
- Add or increase jitter for retry bursts when downstream services are rate-limiting.
- Temporarily throttle producers or disable the affected task producer path if safe.
- Avoid globally raising max retry counts during an active storm.

Resolution evidence:

- Retry rate returns to baseline.
- Failure top no longer concentrates on one dependency or validation error.
- Backlog and oldest pending age stabilize.

## Recovery Count Increase

Primary signals:

- `sum by (task_type) (rate(reliable_task_recovered_total[5m]))` increases.
- Running total may stay high.
- Stale workers may appear.

Diagnosis steps:

1. Check `GET /workers/stale`.
2. Compare handler `timeoutMs()` with real handler duration from logs and slow-task results.
3. Check `worker.lock-ttl-seconds`; it should exceed normal handler duration with operational margin.
4. Inspect worker restarts, deployment events, JVM pauses, and executor queue saturation.
5. Check whether long external calls ignore interruption after timeout cancellation.

Mitigation options:

- Restart unhealthy worker instances.
- Tune handler timeouts and lock TTL only after measuring real execution time.
- Fix handlers that do not honor interruption or lack downstream timeouts.
- Scale capacity if recovery is caused by executor starvation.

Resolution evidence:

- Recovery rate returns to baseline.
- Stale workers disappear.
- Running total drains normally.

## Worker Stale Or Missing

Primary signals:

- `GET /workers/stale` returns one or more workers.
- Backlog rises while capacity drops.
- Recovery count increases after leases expire.

Diagnosis steps:

1. Query `GET /workers` and verify worker IDs, heartbeat times, and host/process identity.
2. Check service deployment status and recent restarts.
3. Verify system clocks are sane across worker hosts.
4. Inspect logs around heartbeat update failures.
5. Confirm database connectivity from worker instances.

Mitigation options:

- Restart or replace the unhealthy worker instance.
- Remove bad deployment versions from traffic.
- Fix database connectivity or credentials through the service's normal secret process.
- Avoid manual database edits unless an incident commander explicitly approves a data repair plan.

Resolution evidence:

- `GET /workers/stale` returns an empty list.
- Worker heartbeat times advance.
- Recovery rate and backlog return to baseline.

## Admin Write Operations

Admin write APIs include retry, cancel, requeue, payload update, and batch operations.
Use them only after root cause is understood and business replay safety is confirmed.

Required checks before writes:

1. `reliable-task.admin.write-enabled=true` is approved for the environment.
2. `reliable-task.admin.auth.enabled=true` is active.
3. Audit logging is enabled when required by the service policy.
4. The operator identity is present in headers and trace context.
5. The affected tasks are filtered by task type, error code, status, and time window.

Safe write pattern:

1. Preview or list the exact task set.
2. Requeue a small sample.
3. Verify timeline and logs.
4. Continue in bounded batches only if the sample succeeds.

## Post-Incident Notes

Record:

- Start and end time.
- Task types and error codes affected.
- PromQL graphs or alert names used.
- Admin API queries used.
- Manual write operations, operator identity, and trace IDs.
- Configuration changes and follow-up tasks.
