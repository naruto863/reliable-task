# ReliableTask Monitoring Guide

This guide describes the production monitoring surface available for the v1.0 release line.
It only references metrics and Admin APIs that exist in the repository today.

For the broader v1.0 adoption example map, see [ReliableTask v1.0 Example Matrix](../review/RELIABLE_TASK_V10_EXAMPLE_MATRIX.md).

## Scope

- Micrometer metrics are emitted only when `reliable-task.metrics.enabled=true` and the application has a Micrometer registry.
- Admin troubleshooting APIs are registered only when `reliable-task.admin.enabled=true`.
- Keep `reliable-task.admin.auth.enabled=true` in production and expose Admin APIs only behind internal network controls.
- The thresholds below are examples for adoption reviews. Tune them per service, task type, traffic pattern, and SLO.
- Do not put database credentials, internal hostnames, bearer tokens, or private URLs into shared dashboards or alert files.

## Existing Metrics

| Meter | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `reliable_task_submitted_total` | Counter | `task_type`, `status` | Task submissions recorded as pending work. |
| `reliable_task_success_total` | Counter | `task_type`, `status` | Successful task executions. |
| `reliable_task_failed_total` | Counter | `task_type`, `status` | Failed execution outcomes, including retrying failures and terminal dead outcomes. |
| `reliable_task_retry_total` | Counter | `task_type`, `status` | Executions scheduled for retry. |
| `reliable_task_execution_duration` | Timer | `task_type`, `status` | Execution duration recorded for success, retrying failure, and terminal failure outcomes. Prometheus exposition may add `_seconds_count`, `_seconds_sum`, or histogram series depending on registry configuration. |
| `reliable_task_pending_total` | Gauge | none | Current pending task count from `TaskStore.getStats()`. |
| `reliable_task_backlog_total` | Gauge | none | Queue backlog; currently the same pending task count used for backlog monitoring. |
| `reliable_task_running_total` | Gauge | none | Current running task count from task statistics. |
| `reliable_task_dead_total` | Gauge | none | Current terminal dead task count from task statistics. |
| `reliable_task_oldest_pending_age_seconds` | Gauge | none | Age in seconds of the oldest pending task. |
| `reliable_task_worker_available_capacity` | Gauge | none | Available executor capacity reported by `TaskExecutorFactory`. |
| `reliable_task_recovered_total` | Counter | `task_type` | Timeout recovery events. |

By default, execution metrics do not include `worker_id` to avoid high-cardinality time series.
Only enable `reliable-task.metrics.include-worker-id-tag=true` for short investigations or controlled low-cardinality deployments.

## Dashboard PromQL

Backlog and queue age:

```promql
sum(reliable_task_backlog_total)
max(reliable_task_oldest_pending_age_seconds)
sum(reliable_task_pending_total)
sum(reliable_task_running_total)
sum(reliable_task_dead_total)
```

Execution throughput and failures:

```promql
sum by (task_type) (rate(reliable_task_submitted_total[5m]))
sum by (task_type) (rate(reliable_task_success_total[5m]))
sum by (task_type) (rate(reliable_task_retry_total[5m]))
sum by (task_type, status) (rate(reliable_task_failed_total[5m]))
sum by (task_type) (rate(reliable_task_failed_total{status="DEAD"}[5m]))
```

Recovery and capacity:

```promql
sum by (task_type) (rate(reliable_task_recovered_total[5m]))
sum(reliable_task_worker_available_capacity)
```

Execution duration depends on the Micrometer Prometheus registry exposition.
For a basic average duration view, use the timer sum/count series when they are available:

```promql
rate(reliable_task_execution_duration_seconds_sum[5m])
/
clamp_min(rate(reliable_task_execution_duration_seconds_count[5m]), 1)
```

For slow task diagnosis, prefer the bounded Admin query because it returns concrete task records:

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/slow?durationMsGte=30000&limit=20" \
  -H "X-Operator: oncall"
```

## Suggested Alert Themes

Use the Prometheus rules in [prometheus-alerts-example.yml](prometheus-alerts-example.yml) as placeholders.
Review every threshold before production use.

| Theme | Primary signal | First follow-up |
| --- | --- | --- |
| Backlog growth | `reliable_task_backlog_total` and `reliable_task_oldest_pending_age_seconds` | Check worker capacity, stale workers, slow tasks, and recent failures. |
| No worker capacity | `reliable_task_worker_available_capacity` with non-zero backlog | Check executor saturation, handler latency, and worker process health. |
| Retry storm | `rate(reliable_task_retry_total[5m])` | Check failure top, recent failures, dependency status, and retry classification. |
| Dead task spike | `rate(reliable_task_failed_total{status="DEAD"}[5m])` and `reliable_task_dead_total` | Check recent failures, failure top, task logs, timeline, and dead-letter handler output. |
| Recovery spike | `rate(reliable_task_recovered_total[5m])` | Check stale workers, handler timeout, lock TTL, and long-running external calls. |
| Stale worker | Admin `GET /workers/stale` | Check deployments, heartbeat freshness, process health, and clock skew. |

## Admin Troubleshooting API

Set `ADMIN_BASE_URL` to the internal Admin base URL for the service under investigation.
The examples intentionally omit credentials; use the service's approved authentication path.

```bash
export ADMIN_BASE_URL="https://reliable-task-admin.example.invalid"
```

Stats and worker health:

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/stats" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/workers" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/workers/stale" -H "X-Operator: oncall"
```

Failure and slow task diagnosis:

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/recent-failures?limit=20" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/failure-top?groupBy=taskType,errorCode&limit=20" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/slow?durationMsGte=30000&limit=20" -H "X-Operator: oncall"
```

Single task drill-down:

```bash
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}/logs" -H "X-Operator: oncall"
curl -sS "$ADMIN_BASE_URL/api/reliable-task/tasks/{taskId}/timeline" -H "X-Operator: oncall"
```

## Dashboard Layout

Recommended first dashboard row:

| Panel | Query |
| --- | --- |
| Backlog | `sum(reliable_task_backlog_total)` |
| Oldest pending age | `max(reliable_task_oldest_pending_age_seconds)` |
| Worker available capacity | `sum(reliable_task_worker_available_capacity)` |
| Retry rate by task type | `sum by (task_type) (rate(reliable_task_retry_total[5m]))` |
| Dead rate by task type | `sum by (task_type) (rate(reliable_task_failed_total{status="DEAD"}[5m]))` |
| Recovery rate by task type | `sum by (task_type) (rate(reliable_task_recovered_total[5m]))` |

Recommended drill-down row:

| Panel | Query |
| --- | --- |
| Pending total | `sum(reliable_task_pending_total)` |
| Running total | `sum(reliable_task_running_total)` |
| Dead total | `sum(reliable_task_dead_total)` |
| Success rate by task type | `sum by (task_type) (rate(reliable_task_success_total[5m]))` |
| Failure rate by status | `sum by (status) (rate(reliable_task_failed_total[5m]))` |
