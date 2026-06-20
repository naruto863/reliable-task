# Security Policy

ReliableTask 涉及异步任务执行、管理 API、payload 存储和数据库配置。请优先以安全方式报告和处理问题。

## 支持版本

| 版本 | 支持状态 |
| --- | --- |
| `1.0.x` | v1.0 release closure 完成后进入稳定支持线 |
| `0.7.x` | v1.0 准备基线，安全修复按 best effort 合入并随 v1.0 发布 |
| `< 0.7.0` | 不再主动支持，请升级到最新预览或 v1.0 稳定版 |

## 安全检查

发布前治理至少包含：

- CodeQL 静态分析：`.github/workflows/codeql.yml` 覆盖 Java 和 JavaScript/TypeScript。
- Dependabot：`.github/dependabot.yml` 跟踪 Maven、npm 和 GitHub Actions 依赖更新。
- Release workflow 可选安全层：`run-security-scan=true` 时输出 Maven dependency tree，并运行 Console `npm audit --audit-level=high`。
- 敏感信息扫描：发布前执行 `rg -n "password|passwd|secret|token|private key|BEGIN .* KEY|jdbc:mysql://|AKIA|ghp_" .` 并人工分辨示例占位符与真实凭证。

## 报告漏洞

请不要在公开 Issue 中披露可利用漏洞、真实密钥、真实内部地址或完整攻击细节。

请通过以下方式私下报告：

- 安全邮箱：shen940126@gmail.com | 52dabaobao@gmail.com
- GitHub Security Advisory：https://github.com/naruto863/reliable-task/security/advisories/new

报告中建议包含：

- 受影响版本或提交。
- 复现步骤。
- 影响范围。
- 已知缓解方式。
- 是否已在公开环境中发现利用迹象。

## 响应流程

- 维护者确认报告并评估影响范围。
- 如问题有效，优先准备修复和回归测试。
- 修复发布后，在 `CHANGELOG.md` 的 `Security` 小节记录。
- 如涉及凭据泄露，应立即轮换凭据，并避免公开泄露值。

## 开源前敏感信息规则

禁止提交：

- 数据库真实账号和密码。
- API Key、Token、Cookie、私钥、证书私钥。
- 内部域名、内部 IP、跳板机、专线地址。
- 真实用户数据、生产订单号、手机号、邮箱、身份证号。
- 本地 `application.yml`、`.env`、IDE 私有工作区配置、构建产物。

示例配置必须放在：

- `.env.example`
- `application-example.yml`

## 生产安全提醒

- Admin API 生产环境必须接入认证、授权、审计和网络访问控制。
- 不要把 Admin 写接口直接暴露到公网。
- payload 中不要保存明文敏感数据；如业务必须保存，请自行加密、脱敏或缩短保留周期。
- 数据库账号应使用最小权限，不建议使用 root 账号。
- 日志和监控中不要输出完整 payload、Token、Cookie 或用户隐私数据。
