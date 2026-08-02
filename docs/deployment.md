# Deployment Guide

## Docker Compose

```bash
cp .env.example .env
vim .env
docker compose up -d --build
docker compose ps
```

Services:

- `mysql`: MySQL 8. The init runner applies formal migrations and skips demo/test scripts.
- `redis`: Redis 7 for session storage.
- `backend`: Spring Boot service with `/api` context path.
- `frontend`: Nginx serving the built Vue SPA and proxying `/api`.

## Required Environment Variables

| Variable | Purpose |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root password |
| `MYSQL_DB` | Database name, normally `erp` (the bundled migrations use this name) |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Application database user |
| `REDIS_PASSWORD` | Redis password |
| `ERP_JWT_SECRET` | JWT signing secret, at least 64 random characters |
| `ERP_BOOTSTRAP_ADMIN_PASSWORD` | Strong password used only to create the first `admin` |
| `ERP_CORS_ORIGINS` | Allowed frontend origins if deployed cross-origin |
| `ERP_COMPANY_NAME` / `ERP_COMPANY_ADDRESS_LINE_*` | Company name and address printed on exported documents |
| `ERP_COMPANY_WEBSITE` / `ERP_COMPANY_CONTACT` | Public website and contact text printed on exported documents |
| `ERP_COMPANY_BANK_*` | Bank beneficiary, account, address, SWIFT code and settlement currency |
| `MAIL_*` | SMTP settings for email verification |

Company and bank variables are intentionally empty or generic in the repository. Put real values only in the deployment `.env`; never commit that file. PI, CI and PO exports read these values at runtime, so no source-code edit is required to customize document headers or remittance instructions.

## Production Checklist

- Store `.env` outside backups and restrict its file permissions.
- Change the bootstrap `admin` password after first sign-in.
- Use `SPRING_PROFILES_ACTIVE=prod`.
- Keep MySQL and Redis ports private.
- Use HTTPS at Nginx or an upstream load balancer.
- Set a strong `ERP_JWT_SECRET`.
- Configure SMTP if registration requires email verification.
- Keep `ERP_MAIL_EXPOSE_CODE=false`.
- Keep API docs disabled in public production environments unless explicitly needed.
- Back up MySQL and uploaded files together.

## Backup

```bash
docker compose exec -T mysql mysqldump -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  > xeflow-backup-$(date +%Y%m%d).sql
docker run --rm -v xeflow_backend-uploads:/data -v "$PWD":/backup alpine \
  tar -czf /backup/xeflow-uploads-$(date +%Y%m%d).tar.gz /data
```

## Update

```bash
git pull
docker compose up -d --build
docker compose logs -f backend
```

## Health Checks

- Backend health: `GET /api/actuator/health`
- Container status: `docker compose ps`
- Backend logs: `docker compose logs -f backend`
- Frontend logs: `docker compose logs -f frontend`
