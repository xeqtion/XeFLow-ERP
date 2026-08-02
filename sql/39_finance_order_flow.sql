-- Finance-only access and PI payment/shipment workflow migration.
USE `erp`;
SET NAMES utf8mb4;

SET @deposit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sales_pi' AND column_name = 'deposit_amount'
);
SET @deposit_sql = IF(@deposit_exists = 0,
  'ALTER TABLE `sales_pi` ADD COLUMN `deposit_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER `total_amount`',
  'SELECT 1');
PREPARE deposit_stmt FROM @deposit_sql;
EXECUTE deposit_stmt;
DEALLOCATE PREPARE deposit_stmt;

UPDATE `sys_permission` SET `sort` = 51 WHERE `id` = 502;
UPDATE `sys_permission` SET `sort` = 52 WHERE `id` = 503;
UPDATE `sys_permission` SET `sort` = 53 WHERE `id` = 501;

-- Sales roles must not see or call finance modules.
DELETE rp FROM `sys_role_permission` rp
JOIN `sys_role` r ON r.`id` = rp.`role_id`
JOIN `sys_permission` p ON p.`id` = rp.`permission_id`
WHERE r.`role_key` IN ('salesman', 'sales_manager')
  AND (p.`id` = 500 OR p.`parent_id` = 500 OR p.`perm_key` LIKE 'finance:%');

-- Finance owns all finance functions and can read/filter PI and document tracking.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_key` = 'finance'
  AND p.`deleted` = 0 AND p.`status` = 1
  AND (p.`id` IN (200, 203, 204, 500, 501, 502, 503)
       OR p.`perm_key` IN ('sales_pi:list', 'trace:view', 'data:all')
       OR p.`perm_key` LIKE 'finance:%');
