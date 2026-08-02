# Security Policy

## Supported Version

安全修复默认进入 `main`。维护者会在发布说明中标注受影响版本。

## Private Reporting

请使用 GitHub Security Advisories 的 **Report a vulnerability** 私下报告。不要在公开 Issue 中披露可利用细节、凭据、客户数据或财务文件。

报告建议包含：受影响版本、复现步骤、影响范围、最小证明和建议修复。维护者会尽快确认并协调披露时间。

## Deployment Baseline

- 首次安装必须自行设置强 `ERP_BOOTSTRAP_ADMIN_PASSWORD`。
- 使用至少 64 位随机 `ERP_JWT_SECRET`，不要复用数据库或邮箱密码。
- MySQL、Redis 和后端管理端口仅监听内网或 loopback。
- 公网部署启用 HTTPS、备份、日志轮转和最小权限安全组。
- 注册默认关闭；启用时使用邀请码、邮箱验证和管理员审核。
- 正式使用前更换示例公司信息，并清理演示数据。

## Data Safety

本仓库不接收包含真实业务数据的 Issue、PR 或附件。请在提交日志和截图前删除邮箱、电话、地址、合同、付款信息、Token 与内部 URL。
