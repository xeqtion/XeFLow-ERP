-- =====================================================================
-- Role and permission structure for the XeFlow open-source edition
-- Roles:
--   #0 super_admin     admin account only, protected, highest privilege
--   #1 admin           same privileges as super_admin, user can be deleted
--   #2 finance         all finance flows, receivable/payable, finance reports
--   #3 sales_manager   all sales/customer/order performance, quote approval
--   #4 salesman        own customers, sales orders, quotes and performance
--   #5 readonly        minimal safe read-only role; extend from role UI as needed
-- Load:
--   mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/35_role_permission_structure.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `_tmp_builtin_user_roles`;
CREATE TEMPORARY TABLE `_tmp_builtin_user_roles` AS
SELECT DISTINCT ur.`user_id`, r.`role_key`
FROM `sys_user_role` ur
JOIN `sys_role` r ON r.`id` = ur.`role_id`
WHERE r.`role_key` IN ('super_admin','admin','finance','sales_manager','salesman','readonly')
   OR r.`id` IN (0,1,2,3,4,5);

DELETE ur
FROM `sys_user_role` ur
JOIN `sys_role` r ON r.`id` = ur.`role_id`
WHERE r.`role_key` IN ('super_admin','admin','finance','sales_manager','salesman','readonly')
   OR r.`id` IN (0,1,2,3,4,5);

DELETE rp
FROM `sys_role_permission` rp
JOIN `sys_role` r ON r.`id` = rp.`role_id`
WHERE r.`role_key` IN ('super_admin','admin','finance','sales_manager','salesman','readonly')
   OR r.`id` IN (0,1,2,3,4,5);

DELETE FROM `sys_role`
WHERE `role_key` IN ('super_admin','admin','finance','sales_manager','salesman','readonly')
   OR `id` IN (0,1,2,3,4,5);

INSERT INTO `sys_role`
  (`id`, `role_name`, `role_key`, `sort`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
  (0, '超级管理员', 'super_admin',   0, 1, '内置最高权限，仅 admin 账号使用，不可删除、不可停用', 1, NOW(), 1, NOW(), 0),
  (1, '管理员',     'admin',         1, 1, '管理员权限与超管一致，账号可按正常用户删除',       1, NOW(), 1, NOW(), 0),
  (2, '财务',       'finance',       2, 1, '查看并维护全部收付款流水、应收、应付和财务报表', 1, NOW(), 1, NOW(), 0),
  (3, '销售主管',   'sales_manager', 3, 1, '查看全部业务员客户、订单、业绩，并审核销售报价', 1, NOW(), 1, NOW(), 0),
  (4, '业务员',     'salesman',      4, 1, '普通跟单，维护自己发展的客户、报价、订单和业绩', 1, NOW(), 1, NOW(), 0),
  (5, '只读权限',   'readonly',      5, 1, '最低安全角色，仅授予基础只读入口，更多权限由管理员分配', 1, NOW(), 1, NOW(), 0);

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT x.`user_id`, x.`role_id`
FROM (
  SELECT DISTINCT
    t.`user_id`,
    CASE
      WHEN t.`user_id` = 1 AND t.`role_key` IN ('super_admin','admin') THEN 0
      WHEN t.`user_id` <> 1 AND t.`role_key` IN ('super_admin','admin') THEN 1
      WHEN t.`role_key` = 'finance' THEN 2
      WHEN t.`role_key` = 'sales_manager' THEN 3
      WHEN t.`role_key` = 'salesman' THEN 4
      WHEN t.`role_key` = 'readonly' THEN 5
      ELSE NULL
    END AS `role_id`
  FROM `_tmp_builtin_user_roles` t
  UNION ALL
  SELECT 1 AS `user_id`, 0 AS `role_id` FROM `sys_user` WHERE `id` = 1
  UNION ALL
  SELECT 2 AS `user_id`, 1 AS `role_id` FROM `sys_user` WHERE `id` = 2
) x
WHERE x.`role_id` IS NOT NULL;

UPDATE `sys_user`
SET `status` = 1,
    `username` = CONCAT('admin_', `id`)
WHERE `id` <> 1
  AND `username` = 'admin'
  AND `deleted` = 0;

UPDATE `sys_user`
SET `status` = 1,
    `username` = 'admin',
    `real_name` = COALESCE(NULLIF(`real_name`, ''), '超级管理员1'),
    `nickname` = 'admin'
WHERE `id` = 1;

UPDATE `sys_user`
SET `real_name` = CASE WHEN `real_name` IS NULL OR `real_name` = '' OR `real_name` LIKE '超级管理员%' THEN '管理员2' ELSE `real_name` END,
    `nickname` = COALESCE(NULLIF(`nickname`, ''), 'admin2')
WHERE `id` = 2;

-- Highest roles: full menus/buttons plus wildcard in backend login.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_key` IN ('super_admin','admin')
  AND p.`status` = 1 AND p.`deleted` = 0;

-- Salesman: own-scope business work, no data:all, no quote approval, no raw finance flow.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 4, p.`id`
FROM `sys_permission` p
WHERE p.`status` = 1 AND p.`deleted` = 0
  AND (
    p.`id` IN (100,200,300,400,600,700,1000,1100)
    OR p.`perm_key` IN (
      'customer:list','customer:create','customer:update','customer:delete','customer:export',
      'product:list','bom:list','currency:list',
      'standard_price:list',
      'contract_price:list','contract_price:create','contract_price:update','contract_price:delete',
      'sales_pi:list','sales_pi:create','sales_pi:update','sales_pi:delete','sales_pi:export',
      'trace:view',
      'order_oem:list','order_oem:create','order_oem:update','order_oem:delete',
      'purchase_order:list','purchase_order:create','purchase_order:update','purchase_order:delete','purchase_order:export',
      'shipping_instruction:list','shipping_instruction:create','shipping_instruction:update','shipping_instruction:delete','shipping_instruction:export',
      'shipment:list','shipment:create','shipment:update','shipment:delete','shipment:export',
      'report:sales',
      'fileshare:list','fileshare:upload',
      'inventory:view',
      'message:list','message:send'
    )
  );

-- Sales manager: all sales/customer/order data, quote approval, sales performance.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 3, p.`id`
FROM `sys_permission` p
WHERE p.`status` = 1 AND p.`deleted` = 0
  AND (
    p.`id` IN (100,200,300,400,600,700,1000,1100)
    OR p.`perm_key` IN (
      'customer:list','customer:create','customer:update','customer:delete','customer:export',
      'product:list','bom:list','currency:list',
      'standard_price:list',
      'contract_price:list','contract_price:create','contract_price:update','contract_price:delete','contract_price:approve',
      'sales_pi:list','sales_pi:create','sales_pi:update','sales_pi:delete','sales_pi:export',
      'trace:view',
      'order_oem:list','order_oem:create','order_oem:update','order_oem:delete',
      'purchase_order:list','purchase_order:create','purchase_order:update','purchase_order:delete','purchase_order:export',
      'shipping_instruction:list','shipping_instruction:create','shipping_instruction:update','shipping_instruction:delete','shipping_instruction:export',
      'shipment:list','shipment:create','shipment:update','shipment:delete','shipment:export',
      'report:sales','data:all',
      'fileshare:list','fileshare:upload','fileshare:delete',
      'inventory:view',
      'message:list','message:send'
    )
  );

-- Finance: all finance flow and finance-linked read access.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 2, p.`id`
FROM `sys_permission` p
WHERE p.`status` = 1 AND p.`deleted` = 0
  AND (
    p.`id` IN (100,200,300,400,500,600,700,1100)
    OR p.`perm_key` IN (
      'customer:list','product:list','bom:list','currency:list',
      'sales_pi:list','trace:view','purchase_order:list','shipment:list',
      'finance:list','finance:create','finance:update','finance:delete','finance:import','finance:export',
      'finance:receivable','finance:payable',
      'report:purchase','report:sales','report:profit','data:all',
      'fileshare:list','fileshare:upload',
      'message:list','message:send'
    )
  );

-- Readonly: intentionally minimal and button-free; admins can extend it from role management.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 5, p.`id`
FROM `sys_permission` p
WHERE p.`status` = 1 AND p.`deleted` = 0
  AND (
    p.`id` IN (100,200,700,1100)
    OR p.`perm_key` IN (
      'product:list','bom:list','currency:list',
      'standard_price:list',
      'fileshare:list',
      'message:list'
    )
  );

-- Seed current pending quote review notices for sales managers, without duplicating same quote/user pair.
INSERT INTO `sys_notification`
  (`id`, `recipient_id`, `title`, `content`, `type`, `group_key`, `is_read`, `create_by`, `create_time`)
SELECT UUID_SHORT(),
       u.`id`,
       '销售报价待审核',
       CONCAT('有销售报价待审核：（报价编号：', cp.`id`,
              '，客户：', COALESCE(NULLIF(cp.`customer_name`, ''), '-'),
              '，产品：', COALESCE(NULLIF(cp.`product_name`, ''), '-'),
              '，型号：', COALESCE(NULLIF(cp.`product_model`, ''), '-'),
              '，成交单价：', cp.`contract_price`, ' ', COALESCE(cp.`currency`, ''),
              '）。请进入销售报价模块审核。'),
       'SYSTEM',
       CONCAT('contract-price-audit-', cp.`id`),
       0,
       cp.`create_by`,
       NOW()
FROM `contract_price` cp
JOIN `sys_user_role` ur ON ur.`role_id` = 3
JOIN `sys_user` u ON u.`id` = ur.`user_id` AND u.`status` = 1 AND u.`deleted` = 0
WHERE cp.`deleted` = 0
  AND cp.`audit_status` = 'PENDING'
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_notification` n
    WHERE n.`recipient_id` = u.`id`
      AND n.`group_key` = CONCAT('contract-price-audit-', cp.`id`)
  );

DROP TEMPORARY TABLE IF EXISTS `_tmp_builtin_user_roles`;
