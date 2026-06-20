# Pull Request

## 变更说明

- 

关联 Issue：

-

## 变更类型

- [ ] feat：新能力
- [ ] fix：缺陷修复
- [ ] docs：文档
- [ ] test：测试
- [ ] ci/chore：仓库维护
- [ ] refactor：不改变行为的重构

## 测试结果

请填写执行过的命令和结果，并使用 `PASS`、`FAIL_CODE`、`BLOCKED_ENV` 或 `NOT_RUN` 标记：

```bash
mvn -B test
```

## 兼容性影响

- [ ] 无兼容性影响
- [ ] 有兼容性影响，已在下方说明
- [ ] 涉及 API/SPI、schema、配置键、Admin API 或 Console capability，已同步文档

说明：

- 

## 安全影响

- [ ] 不涉及安全敏感面
- [ ] 涉及配置、凭据、Admin API、payload、日志或权限，已在下方说明

说明：

- 

## Checklist

- [ ] 没有提交真实密钥、真实账号、真实内部地址、Cookie、Token。
- [ ] 没有提交本地 `application.yml`、`.env`、IDE 私有配置或构建产物。
- [ ] 新增或修改行为已补充测试，或已说明无法自动化测试的原因。
- [ ] 文档和示例配置已同步更新。
- [ ] 如涉及 Console/Admin，已核对 `docs/console-admin-roadmap.md` 的安全边界和非目标。
