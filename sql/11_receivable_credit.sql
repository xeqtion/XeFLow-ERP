-- =====================================================================
-- 应收账款菜单/权限 + 客户信用额度
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/11_receivable_credit.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- 客户信用额度(本位币); 若已存在列会报 Duplicate column, 可安全忽略
ALTER TABLE `customer` ADD COLUMN `credit_limit` DECIMAL(18,2) DEFAULT NULL COMMENT '信用额度(本位币)' AFTER `currency`;

-- 应收账款菜单(财务管理 500 之下) + 查看权限
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  (502, 500, '应收账款', 'finance:receivable', 'C', '/finance/receivable', 'finance/receivable/index', 'Money', 51, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`), `path` = VALUES(`path`), `component` = VALUES(`component`);

-- 授权: 超管 + 业务员/销售主管/财务 (业务员经数据范围只看自己客户的应收)
-- (试运营: 只读新用户不预授应收账款查看, 需超管分配)
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1, 502);
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, 502 FROM `sys_role` r WHERE r.role_key = 'finance';
