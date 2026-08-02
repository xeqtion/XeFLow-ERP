# Development Guide

## Local Setup

```powershell
# Backend
cd erp-backend
mvn spring-boot:run

# Frontend
cd erp-frontend
npm install
npm run dev
```

Create a local `.env` from `.env.example`, set `MYSQL_PASSWORD` and the
bootstrap administrator values, then use the Windows helper:

```powershell
./start-dev.ps1 -Store memory
./stop-dev.ps1
```

## Project Structure

- Backend code is organized by business domain under `com.erp`.
- Controllers expose REST APIs and perform permission annotations.
- Services contain business rules, data-scope checks and transaction boundaries.
- Mappers are MyBatis-Plus data access interfaces.
- Frontend pages call typed API clients from `src/api`.
- Sidebar menus come from the backend permission tree, while route components are statically registered.

## Coding Rules

- Keep business changes inside the relevant domain package.
- Use existing `Result<T>`, `PageQuery`, `PageResult` and exception patterns.
- Add backend permission checks for every write/export/download endpoint.
- Do not rely on frontend-only button hiding for security.
- Keep SQL scripts idempotent where possible and numbered in execution order.
- Do not add sample/demo business data to formal initialization scripts unless explicitly required.
- Never add a known login password to formal seed scripts; use the bootstrap environment variable.

## Documentation Rules

- Update `docs/modules.md` and screenshots when a visible page changes.
- Use PNG screenshots under `docs/assets/screenshots/`.
- Keep sample data under `docs/sample-data/`, not `sql/`, unless it is part of official initialization.
- Keep public docs free of real customer names, secrets and production financial data.

## Checks

```powershell
cd erp-backend
mvn test
mvn -DskipTests compile

cd ..\erp-frontend
npm run type-check
npm run build
```

## Useful URLs

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- OpenAPI/Knife4j: http://localhost:8080/api/doc.html
- Health check: http://localhost:8080/api/actuator/health
