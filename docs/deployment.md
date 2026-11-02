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

## 2026-11-02: User/Role Soft-Delete Unique-Key Patch

Existing installations must explicitly apply `sql/52_soft_delete_unique_keys.sql`.
Docker init scripts run only for a new MySQL volume: `git pull` and a container
rebuild do not update an existing database. New installations get the fixed schema
automatically. This patch needs no application rebuild/restart and does not delete,
rename or disable accounts or change role assignments/permissions.

Back up the database and save `SHOW CREATE TABLE sys_user` and
`SHOW CREATE TABLE sys_role` before applying the patch. From the repository root:

```bash
docker compose exec -T mysql sh -c \
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot "${MYSQL_DATABASE:-erp}"' \
  < sql/52_soft_delete_unique_keys.sql
```

The migration uses a 10-second metadata lock timeout and is safe to run again.
If a long-running transaction blocks it, retry after that transaction finishes.
Each unique index is replaced atomically, retaining uniqueness for active records.

To verify the deployed schema without changing real accounts, run the 14 checks
on session-local temporary tables. Every result line must start with `PASS`:

```bash
docker compose exec -T mysql sh -c \
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names -uroot "${MYSQL_DATABASE:-erp}"' \
  < tests/sql/soft_delete_uniqueness.sql
```

For the complete isolated migration suite used in CI (old bug reproduction,
data preservation, repeat execution and fresh installations), use Python 3 and
a MySQL 8 client with credentials in `MYSQL_PWD` or its client configuration:

```bash
python3 tools/test_soft_delete_uniqueness.py -- mysql -u root
```

This runner creates and then removes only its own randomly named test database.
It can also use a Docker MySQL client; see the script's `--help` output.

The generated `active_unique` marker is `0` for undeleted rows and `NULL` for
deleted rows. MySQL unique indexes allow multiple NULL values, so deleted history
can repeat while undeleted usernames/role keys remain unique. Disabled records
still reserve their names, and original column collations are retained. See the
[MySQL generated-column index documentation](https://dev.mysql.com/doc/refman/8.0/en/create-table-secondary-indexes.html).

Rollback is usually unnecessary because the old application remains compatible.
Do not blindly restore the old indexes: after several same-name history rows
exist, `(username, deleted)` / `(role_key, deleted)` cannot be made unique again.
Keep the backup and use an approved recovery plan if rollback is essential.
