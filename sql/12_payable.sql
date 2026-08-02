-- =====================================================================
-- 应付账款菜单/权限
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/12_payable.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  (503, 500, '应付账款', 'finance:payable', 'C', '/finance/payable', 'finance/payable/index', 'Wallet', 52, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`), `path` = VALUES(`path`), `component` = VALUES(`component`);

-- 授权: 超管 + 财务 + 销售主管 (采购付款不归业务员)
-- (试运营: 只读新用户不预授应付账款查看, 需超管分配)
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1, 503);
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, 503 FROM `sys_role` r WHERE r.role_key = 'finance';
