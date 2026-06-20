# ReliableTask Community Onboarding

This page helps contributors choose the right path before opening an issue or pull request.

## Start Here

- Read `README.md` or `README.zh-CN.md` for the project scope and runtime requirements.
- Read `docs/migration/v1.0-upgrade-guide.md` before reporting upgrade problems.
- Read `docs/console-admin-roadmap.md` before proposing Console or Admin changes.
- Read `SECURITY.md` before reporting a vulnerability or suspected credential exposure.
- Use `CONTRIBUTING.md` for branch, commit, test and PR expectations.

## Choosing an Issue Type

| Need | Where to go | Public details allowed |
| --- | --- | --- |
| Reproducible bug | Bug report issue template | Sanitized logs, versions and reproduction steps. |
| New capability | Feature request issue template | Business scenario, compatibility cost and alternatives. |
| Security vulnerability | GitHub Security Advisory or security email in `SECURITY.md` | Do not open a public issue with exploit details. |
| Console/Admin change | Feature request plus `docs/console-admin-roadmap.md` context | Keep Admin security boundaries explicit. |
| Docs clarification | Issue or PR with `docs:` scope | Avoid pasting private production configuration. |

## Good First Contributions

Good first issues should be small, testable and low-risk:

- documentation examples that do not expose real credentials;
- small Console empty/error-state improvements;
- focused unit tests for existing behavior;
- README, migration guide or roadmap clarifications;
- issue reproduction projects that use sanitized sample data.

Avoid starting with broad rewrites, new storage backends, public Admin exposure, enterprise RBAC/SSO, workflow engines or release automation changes unless maintainers explicitly agree on the design first.

## Pull Request Expectations

- Keep the change focused and describe the user-facing behavior.
- Mention linked issues when available.
- Record verification using `PASS`, `FAIL_CODE`, `BLOCKED_ENV` or `NOT_RUN`.
- Include compatibility impact for API/SPI, schema, configuration, Admin behavior and Console capability changes.
- Include security impact for payload handling, logs, credentials, authorization, audit and network exposure.
- Update docs and examples when user-facing behavior changes.

## Recommended Local Checks

```bash
cmd.exe /c "mvn -Dmaven.repo.local=.m2\repository -B test"
cd reliable-task-console && npm run typecheck && npm run test && npm run build
cd reliable-task-console && npm audit --audit-level=high --registry=https://registry.npmjs.org
git diff --check
```

Use `BLOCKED_ENV` instead of `PASS` when Docker, MySQL, network, npm registry, credentials or GitHub permissions prevent a check from running.

## Maintainer Triage Notes

- Prefer `bug`, `enhancement`, `docs`, `security`, `compatibility`, `admin`, `console`, `good first issue` and `help wanted` labels when available.
- Do not close release-readiness issues until the relevant code or documentation is merged and the verification result is recorded.
- Do not ask reporters to share real credentials, production URLs, cookies, tokens or full payloads.
- Redirect exploitable security details to private disclosure channels.
