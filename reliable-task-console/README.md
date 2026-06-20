# ReliableTask Console

ReliableTask Console is the standalone Vue/Vite preview console for the v1.0 release line.

For the broader v1.0 scenario map, see [ReliableTask v1.0 Example Matrix](../docs/v1.0/RELIABLE_TASK_V10_EXAMPLE_MATRIX.md). It links console-safe troubleshooting, Admin safeguards, monitoring and migration examples without embedding production credentials or private URLs.

For preview scope, Admin safety boundaries and non-goals, see [Console and Admin Roadmap](../docs/console-admin-roadmap.md).

## Local Development

Start the demo backend from the repository root:

```bash
mvn -pl reliable-task-demo -am spring-boot:run
```

Then start the standalone console:

```bash
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite dev server proxies `/api/reliable-task` to
`http://localhost:8080` by default. Override the target in `.env.local` when the demo backend runs
elsewhere:

```bash
VITE_RELIABLE_TASK_PROXY_TARGET=http://localhost:8080
```

The demo `application-example.yml` enables Admin APIs for local exploration, but keeps authorization
and audit disabled. The console therefore shows read-only troubleshooting views and disables write
buttons with the backend reason. To exercise write UI against a local-only backend, enable
`reliable-task.admin.write-enabled=true`, `reliable-task.admin.auth.enabled=true`, and
`reliable-task.admin.audit.enabled=true`, then provide an authorization provider suitable for that
local environment. Do not use that shortcut as production guidance.

Common local states:

- `Admin disabled`: backend did not enable `reliable-task.admin.enabled=true` or did not include the
  Admin starter.
- `API unreachable`: Vite proxy cannot reach the backend target.
- `Access denied`: backend authorization denied the current `X-Operator`.
- `Audit log is disabled`: audit APIs are unavailable until `reliable-task.admin.audit.enabled=true`.
- `Reveal disabled`: payload plaintext is disabled; the UI should only show the console-safe preview.

## Validation

```bash
npm run typecheck
npm run test -- --run
npm run build
npm run test:smoke
```

Smoke tests mock the Admin API and cover the read-only troubleshooting path without requiring a live
demo backend.

## Static Deployment

The console is a standalone frontend. It is not part of the Maven reactor and is not bundled into the
worker starter, Admin starter, or demo jar.

Build static assets:

```bash
npm ci
npm run build
```

Deploy `dist/` behind the same internal operations boundary as the Admin API. A reverse proxy should:

- serve the console static files;
- proxy `VITE_RELIABLE_TASK_API_BASE` (default `/api/reliable-task`) to the Spring Boot application
  that enabled `reliable-task-admin-spring-boot-starter`;
- preserve operator and trace headers required by the host environment;
- keep the console off the public internet unless the surrounding application provides production
  authentication, authorization, audit logging, network controls, and monitoring.

Production deployments should keep `payload-plaintext-enabled=false` unless a specific operational
policy allows plaintext reveal. Write buttons remain disabled until backend capabilities report
`writeEnabled=true`, `authEnabled=true`, `auditEnabled=true`, and the confirmation requirement is met.
