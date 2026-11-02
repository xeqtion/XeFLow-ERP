# Changelog

All notable project-level changes should be recorded here.

## Unreleased

### 2026-11-02 — Repeatable user and role soft deletion

- Fix duplicate-key failures when a previously deleted username or role key is reused and deleted again. Unique indexes now constrain only undeleted records, including disabled accounts/roles.
- Preserve deleted history, existing identifiers, permissions, string collations and the `deleted=0/1` convention; no application restart is required.
- Update fresh-install schemas and add the repeatable `sql/52_soft_delete_unique_keys.sql` upgrade for existing databases. Pulling code or rebuilding containers alone does not migrate an existing MySQL volume.
- Add isolated MySQL 8 CI coverage for the old failure, account/role data preservation, repeated migration, fresh installations and 14 temporary-table regression checks per scenario.

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
