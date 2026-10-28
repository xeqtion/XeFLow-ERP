# Changelog

All notable project-level changes should be recorded here.

## Unreleased

### 2026-10-28 — Lossless identifiers across JSON and browser forms

- Serialize database identifiers (`id`, `*Id`, `createBy`, `updateBy`) and ID collections as JSON strings throughout HTTP entities and DTOs, including nested objects. Pagination, counts, monetary values and quantities remain JSON numbers.
- Return string IDs from create/generate endpoints and role-permission lists; accept string IDs on existing write endpoints without database migrations.
- Support string IDs in frontend API types, permission trees, built-in-role checks, message selection and user actions.
- Preserve stored PI/SI references when editing shipments, including requests from cached clients with rounded IDs; submit only editable shipment fields.
- Add serialization, browser identifier and shipment-edit regression coverage. Refresh open browser tabs after upgrading.

- Added GitHub-style project documentation structure.
- Added screenshot-based module documentation.
- Added local-only documentation sample data script.
- Documented architecture, development, deployment, API, RBAC and troubleshooting workflows.
