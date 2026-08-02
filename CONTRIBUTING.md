# Contributing to XeFlow

感谢你帮助改进 XeFlow。请先搜索已有 Issue，较大的行为变更建议先开 Discussion 或 Issue 对齐业务规则。

## 开发流程

1. Fork 仓库并从 `main` 创建功能分支。
2. 一次提交只解决一个清晰问题。
3. 后端权限和数据范围必须由服务端执行，不能只隐藏前端按钮。
4. UI 变化同步更新文档和必要截图。
5. 提交 Pull Request，说明问题、方案、测试和兼容性影响。

## 本地检查

```bash
cd erp-backend
mvn test

cd ../erp-frontend
npm ci
npm run build
```

## 数据与安全

- 禁止提交真实客户、供应商、订单、银行账户、服务器地址或数据库备份。
- 禁止提交 `.env`、Token、Cookie、SMTP 授权码、私钥或云平台凭据。
- 测试数据只使用 `example.com`、保留 IP 段和明确的虚构名称。
- 新接口要补充鉴权、数据范围校验、参数校验和审计脱敏。

## 代码风格

- Java 遵循现有 Controller / Service / Mapper 分层。
- Vue 页面复用现有 API 层、权限指令和 Element Plus 交互模式。
- 数据库迁移采用递增编号，保持幂等并说明回滚策略。
- 对复杂业务规则增加测试，避免无关格式化或大范围重构。
