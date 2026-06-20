# ReliableTask v1.0 Open Source Governance Report

- check_date: 2026-06-14
- target_release: `v1.0.0`
- baseline_version: `0.7.0`
- scope: source tree, public docs, GitHub workflows, license/security/contribution files, Maven dependency inventory, Console npm audit, sensitive information scan

## Summary

ReliableTask has the repository governance files required for the v1.0 stable open-source release path:

- Apache-2.0 `LICENSE`.
- `SECURITY.md` with supported version policy, private disclosure route and release-time security checks.
- `CONTRIBUTING.md` with Java, Console, dependency inventory and npm audit validation commands.
- `docs/community-onboarding.md` with issue routing, first contribution guidance, verification result labels and maintainer triage notes.
- Issue and PR templates under `.github`.
- CI, release dry-run, CodeQL and Dependabot configuration under `.github`.
- Release process, upgrade guide, test matrix and example matrix under `docs`.

Maven Central publication, tag creation and GitHub Release creation remain out of scope for this report. Those actions belong to TASK-013 and require explicit user authorization plus Central/GPG/GitHub credentials.

## Governance Files

| Area | File | v1.0 status |
| --- | --- | --- |
| License | `LICENSE` | Apache-2.0; copyright holder normalized to ReliableTask contributors. |
| Security policy | `SECURITY.md` | Supports v1.0 stable line after release closure, 0.7.x best-effort preview baseline, private disclosure and advisory route. |
| Contribution guide | `CONTRIBUTING.md` | Documents Java, Console and dependency validation commands, SemVer, PR expectations and v1.0 release governance. |
| Community onboarding | `docs/community-onboarding.md` | Documents issue routing, good first contributions, validation status labels and maintainer triage notes. |
| PR template | `.github/pull_request_template.md` | Requires test, compatibility and security impact notes. |
| Issue templates | `.github/ISSUE_TEMPLATE/*.md` | Bug reports require environment details and sanitized logs; feature requests ask for compatibility and schema cost. |
| CI | `.github/workflows/ci.yml` | Runs Maven tests and Console typecheck/test/build by default; optional MySQL and Playwright smoke remain manual. |
| Release workflow | `.github/workflows/release.yml` | Provides dry-run, optional MySQL, optional Console smoke, optional security scan and guarded Maven Central publish. |
| Static analysis | `.github/workflows/codeql.yml` | Adds CodeQL for Java and JavaScript/TypeScript with extended security queries. |
| Dependency updates | `.github/dependabot.yml` | Tracks Maven, npm and GitHub Actions weekly. |

## Dependency Governance

### Maven

The old direct `mvn -B -DskipTests dependency:tree` command resolved `maven-dependency-plugin:2.8` and failed inside the multi-module reactor by trying to fetch `com.reliabletask:reliable-task-core:0.7.0` from the remote mirror.

The v1.0 command is now pinned to the newer dependency plugin:

```bash
mvn -B -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

2026-06-14 result: `PASS`. The command listed all eight reactor modules and completed with `BUILD SUCCESS`.

### Console npm audit

The first audit attempt against the local npm mirror failed because the mirror did not implement the audit API:

```bash
npm audit --audit-level=high
```

2026-06-14 result: `BLOCKED_ENV` for the mirror endpoint, not a package result.

The audit was rerun against the official npm registry:

```bash
npm audit --audit-level=high --registry=https://registry.npmjs.org
```

Initial result: `FAIL_CODE`. The audit found high/critical development-toolchain advisories in the Console chain around `vite`/`esbuild`/`vitest` plus a `glob` advisory.

Fix applied:

- non-forced `npm audit fix` to update safe transitive packages;
- upgraded direct Console dev dependencies to `vite ^8.0.16`, `@vitejs/plugin-vue ^6.0.7` and `vitest ^4.1.8`;
- removed accidental direct internal dependencies after the upgrade.

Post-fix result: `PASS`, `found 0 vulnerabilities`.

Regression checks after the toolchain upgrade:

- `PASS`: `npm run typecheck`.
- `PASS`: `npm run test -- --run`, 15 test files and 49 tests passed.
- `PASS`: `npm run build`, Vite production build completed.

## Sensitive Information Scan

Command:

```bash
rg -n "password|passwd|secret|token|private key|BEGIN .* KEY|jdbc:mysql://|AKIA|ghp_" .
```

2026-06-14 classification:

- `PASS_WITH_FALSE_POSITIVES`: no real credential, token, private key, production database URL, internal host, cookie or real user data was identified.
- Expected example hits: `.env.example`, README/demo local `jdbc:mysql://localhost` examples, local MySQL integration-test property names, release-process secret names and Console/Admin redaction tests.
- Expected package-name hits: `css-tokenizer`, `js-tokens` and similar npm package names in `package-lock.json`.
- Expected security guidance hits: documentation warning users not to commit passwords, tokens, private keys or credentials.

Before TASK-013 release closure, rerun the same command after release notes and readiness report are finalized.

## Static Analysis and Advisory Path

- CodeQL is configured for pull requests, pushes to `main`, weekly schedule and manual dispatch.
- Dependabot is configured for Maven, npm and GitHub Actions.
- CodeQL and Dependabot require GitHub-side execution after these workflow files are committed and pushed; local validation can only verify file content and diff hygiene.
- Release workflow `run-security-scan=true` gives maintainers a manual pre-release dependency snapshot without making ordinary PRs depend on network-heavy scans.

## Release Caveats

- Real Maven Central publish, Central close/release, GitHub tag and GitHub Release are not performed by this task.
- CodeQL results are not available until the workflow is present on GitHub and runs there.
- Dependabot alerts require repository-side Dependabot/security settings to be enabled.
- Real MySQL readiness remains governed by the TASK-003/TASK-004 caveat: Testcontainers Docker or local MySQL profile must pass before release notes can claim real MySQL validation.

## Conclusion

The v1.0 open-source governance path is ready for the readiness task: static analysis and dependency-update workflows are defined, dependency inventory/audit commands are documented, the Console npm audit findings were fixed and regression-checked, and sensitive scan results are classified as non-secret false positives.
