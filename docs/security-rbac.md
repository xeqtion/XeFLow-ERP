# Security and RBAC

## Roles

| ID | Role | Intended Use |
| --- | --- | --- |
| #0 | 超级管理员 `super_admin` | Highest privilege. `admin` is protected and must not be deleted or disabled. |
| #1 | 管理员 `admin` | Same operational permissions as super admin, but accounts can be managed normally. |
| #2 | 财务 `finance` | Finance entries, receivable, payable and finance reports. |
| #3 | 销售主管 `sales_manager` | View all sales data, assign/follow sales work and approve sales quotes. |
| #4 | 业务员 `salesman` | Maintain own customers, quotes, PI, OEM, PO, shipments and performance data. |
| #5 | 只读权限 `readonly` | Minimal safe role. Extra read permissions are assigned by administrators. |

## Permission Model

- Menu and button permissions are stored in `sys_permission`.
- Role permissions are stored in `sys_role_permission`.
- User roles are stored in `sys_user_role`.
- Backend methods use permission checks; frontend button hiding is only a user-experience layer.
- Administrators and super administrators receive wildcard backend authority.

## Data Scope

- `data:all` or wildcard access can view team-wide or system-wide business data.
- Sales users are restricted by customer owner, PI owner, or creator fallback.
- Customer attachments, OEM files, document exports and finance registration must re-check data visibility.
- Sales supervisors can view all salespeople's customers, orders, quotes and performance.
- Finance users can view finance flows and receivable/payable data, but should not approve sales quotes.

## Audit and Sensitive Data

- Operations are logged through backend audit aspects.
- Sensitive keys such as `password`, `oldPassword`, `newPassword`, `token`, `code` and `registerCode` must be masked.
- Uploaded file objects should be summarized, not serialized into audit logs.

## File Security

- Download paths must be normalized before use.
- The resolved file path must remain inside the configured upload root.
- Download responses should include `X-Content-Type-Options: nosniff`.
- Upload limits and dangerous-extension blocking should be kept enabled.

## Secure Defaults

- New registered users default to `readonly`.
- Registration is disabled by default. When enabled, use administrator-generated invitation codes.
- Email verification should be enabled when SMTP is configured.
- No login password is stored in source control. The first admin password must be supplied through the environment.
