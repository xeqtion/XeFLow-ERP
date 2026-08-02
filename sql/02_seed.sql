-- =====================================================================
-- XeFlow built-in roles, permissions and menus.
-- No user or password is stored in source control. The backend creates the
-- first admin account from ERP_BOOTSTRAP_ADMIN_PASSWORD on an empty database.
-- =====================================================================
USE `erp`;

-- 确保会话以 utf8mb4 解析本文件 (避免 Windows 客户端默认 gbk 导致中文乱码)
SET NAMES utf8mb4;

-- 角色
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `sort`, `status`, `create_time`) VALUES
  (0, '超级管理员', 'super_admin',    0, 1, NOW()),
  (1, '管理员',     'admin',          1, 1, NOW()),
  (2, '财务',       'finance',        2, 1, NOW()),
  (3, '销售主管',   'sales_manager',  3, 1, NOW()),
  (4, '业务员',     'salesman',       4, 1, NOW()),
  (5, '只读权限',   'readonly',       5, 1, NOW())
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- 权限 / 菜单
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `path`, `component`, `icon`, `sort`, `status`, `create_time`) VALUES
  -- 基础资料
  (100, 0,   '基础资料',     NULL,                     'M', '/basedata',            NULL,                          'Files',     10, 1, NOW()),
  (101, 100, '客户资料',     'customer:list',          'C', '/basedata/customer',   'basedata/customer/index',     'User',      11, 1, NOW()),
  (102, 100, '产品资料',     'product:list',           'C', '/basedata/product',    'basedata/product/index',      'Goods',     12, 1, NOW()),
  (103, 100, '产品配件资料', 'bom:list',               'C', '/basedata/bom',        'basedata/bom/index',          'List',      13, 1, NOW()),
  -- 销售管理
  (200, 0,   '销售管理',     NULL,                     'M', '/sales',               NULL,                          'Sell',      20, 1, NOW()),
  (201, 200, '产品报价',     'standard_price:list',    'C', '/sales/standard-price','sales/standardPrice/index',   'PriceTag',  21, 1, NOW()),
  (202, 200, '销售报价',     'contract_price:list',    'C', '/sales/contract-price','sales/contractPrice/index',   'Money',     22, 1, NOW()),
  (2021,202, '报价审核',     'contract_price:approve', 'B', NULL,                   NULL,                          NULL,        1,  1, NOW()),
  (203, 200, '销售合同PI',   'sales_pi:list',          'C', '/sales/pi',            'sales/pi/index',              'Document',  23, 1, NOW()),
  -- 订单管理
  (300, 0,   '订单管理',     NULL,                     'M', '/order',               NULL,                          'Tickets',   30, 1, NOW()),
  (301, 300, 'OEM要求',      'order_oem:list',         'C', '/order/oem',           'order/oem/index',             'Stamp',     31, 1, NOW()),
  (302, 300, 'PO采购订单',   'purchase_order:list',    'C', '/order/po',            'order/po/index',              'ShoppingCart', 32, 1, NOW()),
  (303, 300, '出货指示',     'shipping_instruction:list','C','/order/shipping-instruction','order/shippingInstruction/index','Promotion',33,1,NOW()),
  -- 出货管理
  (400, 0,   '出货管理',     NULL,                     'M', '/shipment',            NULL,                          'Van',       40, 1, NOW()),
  (401, 400, '出货单',       'shipment:list',          'C', '/shipment/list',       'shipment/index',              'Box',       41, 1, NOW()),
  -- 财务管理
  (500, 0,   '财务管理',     NULL,                     'M', '/finance',             NULL,                          'Wallet',    50, 1, NOW()),
  (501, 500, '收付款',       'finance:list',           'C', '/finance/entry',       'finance/entry/index',         'Coin',      53, 1, NOW()),
  -- 管理报表
  (600, 0,   '管理报表',     NULL,                     'M', '/report',              NULL,                          'DataLine',  60, 1, NOW()),
  (601, 600, '采购报表',     'report:purchase',        'C', '/report/purchase',     'report/purchase/index',       'TrendCharts',61,1, NOW()),
  (602, 600, '销售报表',     'report:sales',           'C', '/report/sales',        'report/sales/index',          'Histogram', 62, 1, NOW()),
  (603, 600, '财务利润报表', 'report:profit',          'C', '/report/profit',       'report/profit/index',         'PieChart',  63, 1, NOW()),
  -- 系统管理
  (900, 0,   '系统管理',     NULL,                     'M', '/system',              NULL,                          'Setting',   90, 1, NOW()),
  (901, 900, '用户管理',     'system:user:list',       'C', '/system/user',         'system/user/index',           'UserFilled',91, 1, NOW()),
  (9011,901, '新增用户',     'system:user:create',     'B', NULL, NULL, NULL, 1, 1, NOW()),
  (9012,901, '修改用户',     'system:user:update',     'B', NULL, NULL, NULL, 2, 1, NOW()),
  (9013,901, '删除用户',     'system:user:delete',     'B', NULL, NULL, NULL, 3, 1, NOW()),
  (9015,901, '查看邀请码',   'system:user:invite:list','B', NULL, NULL, NULL, 5, 1, NOW()),
  (9016,901, '生成邀请码',   'system:user:invite:create','B', NULL, NULL, NULL, 6, 1, NOW()),
  (9017,901, '删除邀请码',   'system:user:invite:delete','B', NULL, NULL, NULL, 7, 1, NOW()),
  (902, 900, '角色管理',     'system:role:list',       'C', '/system/role',         'system/role/index',           'Avatar',    92, 1, NOW()),
  (903, 900, '权限菜单',     'system:permission:list', 'C', '/system/permission',   'system/permission/index',     'Menu',      93, 1, NOW()),
  (904, 900, '审计日志',     'system:audit:list',      'C', '/system/audit',        'system/audit/index',          'Document',  94, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- P1 基础资料 按钮级权限 (type B, 挂在对应菜单下)
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (1011, 101, '新增客户', 'customer:create', 'B', 1, 1, NOW()),
  (1012, 101, '修改客户', 'customer:update', 'B', 2, 1, NOW()),
  (1013, 101, '删除客户', 'customer:delete', 'B', 3, 1, NOW()),
  (1014, 101, '导入客户', 'customer:import', 'B', 4, 1, NOW()),
  (1015, 101, '导出客户', 'customer:export', 'B', 5, 1, NOW()),
  (1021, 102, '新增产品', 'product:create',  'B', 1, 1, NOW()),
  (1022, 102, '修改产品', 'product:update',  'B', 2, 1, NOW()),
  (1023, 102, '删除产品', 'product:delete',  'B', 3, 1, NOW()),
  (1024, 102, '导入产品', 'product:import',  'B', 4, 1, NOW()),
  (1025, 102, '导出产品', 'product:export',  'B', 5, 1, NOW()),
  (1031, 103, '新增配件', 'bom:create',      'B', 1, 1, NOW()),
  (1032, 103, '修改配件', 'bom:update',      'B', 2, 1, NOW()),
  (1033, 103, '删除配件', 'bom:delete',      'B', 3, 1, NOW()),
  (1034, 103, '导入配件', 'bom:import',      'B', 4, 1, NOW()),
  (1035, 103, '导出配件', 'bom:export',      'B', 5, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- P2 销售 按钮级权限 (contract_price:approve 已在菜单种子中)
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (2011, 201, '新增报价', 'standard_price:create', 'B', 1, 1, NOW()),
  (2012, 201, '修改报价', 'standard_price:update', 'B', 2, 1, NOW()),
  (2013, 201, '删除报价', 'standard_price:delete', 'B', 3, 1, NOW()),
  (2014, 201, '导入报价', 'standard_price:import', 'B', 4, 1, NOW()),
  (2015, 201, '导出报价', 'standard_price:export', 'B', 5, 1, NOW()),
  (2022, 202, '新增报价', 'contract_price:create', 'B', 2, 1, NOW()),
  (2023, 202, '修改报价', 'contract_price:update', 'B', 3, 1, NOW()),
  (2024, 202, '删除报价', 'contract_price:delete', 'B', 4, 1, NOW()),
  (2031, 203, '新增PI',    'sales_pi:create',       'B', 1, 1, NOW()),
  (2032, 203, '修改PI',    'sales_pi:update',       'B', 2, 1, NOW()),
  (2033, 203, '删除PI',    'sales_pi:delete',       'B', 3, 1, NOW()),
  (2034, 203, '导出PI单据','sales_pi:export',       'B', 4, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- P3 订单+出货 按钮级权限
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (3011, 301, '新增OEM',   'order_oem:create',            'B', 1, 1, NOW()),
  (3012, 301, '修改OEM',   'order_oem:update',            'B', 2, 1, NOW()),
  (3013, 301, '删除OEM',   'order_oem:delete',            'B', 3, 1, NOW()),
  (3021, 302, '新增PO',    'purchase_order:create',       'B', 1, 1, NOW()),
  (3022, 302, '修改PO',    'purchase_order:update',       'B', 2, 1, NOW()),
  (3023, 302, '删除PO',    'purchase_order:delete',       'B', 3, 1, NOW()),
  (3024, 302, '导出PO单据','purchase_order:export',       'B', 4, 1, NOW()),
  (3031, 303, '新增出货指示', 'shipping_instruction:create', 'B', 1, 1, NOW()),
  (3032, 303, '修改出货指示', 'shipping_instruction:update', 'B', 2, 1, NOW()),
  (3033, 303, '删除出货指示', 'shipping_instruction:delete', 'B', 3, 1, NOW()),
  (3034, 303, '导入出货指示', 'shipping_instruction:import', 'B', 4, 1, NOW()),
  (3035, 303, '导出出货指示', 'shipping_instruction:export', 'B', 5, 1, NOW()),
  (4011, 401, '新增出货单', 'shipment:create',             'B', 1, 1, NOW()),
  (4012, 401, '修改出货单', 'shipment:update',             'B', 2, 1, NOW()),
  (4013, 401, '删除出货单', 'shipment:delete',             'B', 3, 1, NOW()),
  (4014, 401, '导入出货单', 'shipment:import',             'B', 4, 1, NOW()),
  (4015, 401, '导出出货单', 'shipment:export',             'B', 5, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- P4 财务 按钮级权限
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (5011, 501, '新增收付款', 'finance:create', 'B', 1, 1, NOW()),
  (5012, 501, '修改收付款', 'finance:update', 'B', 2, 1, NOW()),
  (5013, 501, '删除收付款', 'finance:delete', 'B', 3, 1, NOW()),
  (5014, 501, '导入收付款', 'finance:import', 'B', 4, 1, NOW()),
  (5015, 501, '导出收付款', 'finance:export', 'B', 5, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- Highest roles receive all permissions. The bootstrap admin is assigned role 0
-- by BootstrapAdminInitializer after schema initialization has completed.
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_key` IN ('super_admin', 'admin')
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
