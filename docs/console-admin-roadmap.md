# ReliableTask Console and Admin Roadmap

ReliableTask Console is a preview operations UI for the v1.x line. It is intentionally separate from the Java Maven reactor and from both Spring Boot starters.

## Current Boundary

- Console static assets are built from `reliable-task-console/` and deployed independently.
- Admin REST APIs are registered only when applications add `reliable-task-admin-spring-boot-starter` and set `reliable-task.admin.enabled=true`.
- Admin APIs are internal operations surfaces. They are not a public SaaS console and should be deployed behind application authentication, authorization, audit logging, network controls and monitoring.
- Admin writes are disabled by default. When enabled, server-side checks still require write enablement, authorization, audit and `X-Confirm-Operation: true` unless a host application deliberately changes that policy.
- Payload plaintext is disabled by default. Console-safe detail APIs should expose previews and metadata unless the host application explicitly allows plaintext reveal.

## Implemented Experience

- Dashboard-oriented read-only troubleshooting views.
- Task list, task detail, status timeline and task logs.
- Worker and stale-worker inspection.
- Audit-log views when audit is enabled.
- Console capability reporting so the UI can disable unsafe actions with backend-provided reasons.
- Guarded single-task and batch operation flows when the backend exposes write capability.

## v1.x Roadmap

| Area | Direction | Guardrail |
| --- | --- | --- |
| Read-only diagnostics | Improve failure aggregation, retry visibility, worker health and empty/error states. | No new production write path is required. |
| Guarded operations | Refine retry, requeue, cancel, payload update and batch previews. | Keep server-side write, auth, audit and confirmation checks authoritative. |
| Deployment guidance | Document reverse proxy headers, API base path, static hosting and internal access boundaries. | Do not bundle Console into worker/admin starters or demo jars. |
| Observability | Align Console cards with metrics, alert and runbook documents. | Avoid high-cardinality tags and raw payload exposure. |
| Compatibility | Keep Admin API and Console capability contracts versioned through docs and tests. | Do not remove existing v1.0 Admin API compatibility without a later migration note. |

## Explicit Non-Goals

- Enterprise RBAC or SSO productization.
- Organization, tenant, workspace or billing management.
- Public internet exposure without a host application security boundary.
- Cross-cluster control plane or multi-application fleet management.
- Replacing existing application authentication, authorization or audit systems.
- Bundling Console into core worker libraries or making it required for task execution.

## Validation

Before changing Console/Admin behavior, run the relevant checks:

```bash
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B -pl reliable-task-admin,reliable-task-admin-spring-boot-starter,reliable-task-spring-boot-starter -am test"
cd reliable-task-console && npm run typecheck && npm run test && npm run build
```

For security-sensitive Admin changes, also review `SECURITY.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md` and the release readiness report.
