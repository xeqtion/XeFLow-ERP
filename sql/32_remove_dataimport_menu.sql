-- Remove Data Import module from menu/permission tree.

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.id = 800 OR p.path = '/dataimport' OR p.perm_key = 'dataimport:manage';

DELETE FROM sys_permission
WHERE id = 800 OR path = '/dataimport' OR perm_key = 'dataimport:manage';
