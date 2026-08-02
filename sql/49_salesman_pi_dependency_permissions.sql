-- Keep the sales PI form's reference-data permissions aligned with the
-- permissions assigned to sales roles. This migration is safe to rerun.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
  ON p.perm_key IN ('standard_price:list', 'currency:list')
 AND p.status = 1
 AND p.deleted = 0
WHERE r.role_key IN ('salesman', 'sales_manager')
  AND r.status = 1
  AND r.deleted = 0;
