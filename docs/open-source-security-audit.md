# 开源安全审查记录

## 审查目标

本开源副本从私有业务项目的已提交源码导出，未复制原 Git 历史。审查范围包括凭据、部署配置、数据库初始化、演示数据、品牌与文档元数据。

## 已处理事项

- 移除生产域名、服务器 IP、公司邮箱和原公司品牌素材。
- 未复制本地 `.env`、部署凭据、日志、上传目录、数据库备份和构建产物。
- 删除源码中的通用管理员账号与公开密码。
- 首次空库启动改为通过 `ERP_BOOTSTRAP_ADMIN_PASSWORD` 生成唯一 `admin`。
- Docker 初始化只执行正式 schema 与迁移，跳过演示和权限测试脚本。
- 注册默认关闭，SMTP 默认关闭，API 文档在生产 profile 关闭。
- 出货单据中的公司名称、地址、网站和唛头改为环境变量。
- 所有前端、邮件和导出品牌统一为 XeFlow。
- 增加 GitHub CI、Dependabot 和 Gitleaks 检查。

## 发布前检查

```bash
git grep -n -I -E '(BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|xox[baprs]-)'
docker compose config
cd erp-backend && mvn test
cd ../erp-frontend && npm ci && npm run build
```

## 使用者责任

XeFlow 无法替代部署方的安全评估。生产使用前应完成权限矩阵复核、数据备份恢复演练、漏洞扫描、HTTPS 配置、日志与告警配置，并核对适用的数据保护和财务合规要求。
