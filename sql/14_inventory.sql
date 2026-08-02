-- =====================================================================
-- 库存模块: 出入库流水 + 产品安全库存 + 菜单/权限
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/14_inventory.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `stock_movement` (
  `id`             BIGINT       NOT NULL,
  `prod_code`      VARCHAR(64)  NOT NULL COMMENT '产品代码',
  `product_id`     BIGINT                DEFAULT NULL,
  `product_name`   VARCHAR(255)          DEFAULT NULL,
  `direction`      VARCHAR(8)   NOT NULL COMMENT 'IN/OUT',
  `qty`            DECIMAL(18,4)         DEFAULT NULL,
  `biz_type`       VARCHAR(32)           DEFAULT 'MANUAL' COMMENT 'PURCHASE_IN/SALE_OUT/BOM_OUT/ADJUST/MANUAL',
  `related_doc_no` VARCHAR(64)           DEFAULT NULL,
  `move_date`      DATE                  DEFAULT NULL,
  `remark`         VARCHAR(512)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_prod` (`prod_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存出入库流水';

-- 产品安全库存(已存在列会报 Duplicate column, 可安全忽略)
ALTER TABLE `product` ADD COLUMN `safety_stock` DECIMAL(18,4) DEFAULT NULL COMMENT '安全库存' AFTER `currency`;

-- 菜单与权限: 库存管理(顶级 M) -> 库存查询(C)
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  (1000, 0,    '库存管理', NULL,               'M', '/inventory',       NULL,                   'Box',   45, 1, NOW()),
  (1001, 1000, '库存查询', 'inventory:view',   'C', '/inventory/stock', 'inventory/stock/index', 'Box',  1, 1, NOW()),
  (1002, 1000, '出入库登记', 'inventory:manage','B', NULL, NULL, NULL, 2, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`), `path` = VALUES(`path`), `component` = VALUES(`component`);

-- 授权: 超管全部; view 给 业务员/主管/财务; manage 给 主管
-- (试运营: 只读新用户不预授库存查询, 需超管分配)
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1, 1000), (1, 1001), (1, 1002);
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE p.id IN (1000, 1001) AND r.role_key IN ('salesman','sales_manager','finance');
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, 1002 FROM `sys_role` r WHERE r.role_key = 'sales_manager';
