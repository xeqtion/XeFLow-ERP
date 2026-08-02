-- =====================================================================
-- 资料共享 + 通用Excel导入 + 业务员数据权限
-- 加载(务必用 source, 避免 PowerShell 管道乱码):
--   mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/08_fileshare_import_datascope.sql"
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- ---------- 资料共享 ----------
CREATE TABLE IF NOT EXISTS `shared_file` (
  `id`            BIGINT       NOT NULL,
  `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `stored_name`   VARCHAR(128) NOT NULL COMMENT '磁盘存储名',
  `content_type`  VARCHAR(128)          DEFAULT NULL,
  `file_format`   VARCHAR(32)           DEFAULT NULL COMMENT '文件格式/后缀',
  `size`          BIGINT                DEFAULT NULL,
  `category`      VARCHAR(64)           DEFAULT '无',
  `uploader_name` VARCHAR(64)           DEFAULT NULL,
  `remark`        VARCHAR(255)          DEFAULT NULL,
  `expire_time`   DATETIME              DEFAULT NULL COMMENT '过期时间',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享文件';

-- ---------- 通用导入 ----------
CREATE TABLE IF NOT EXISTS `import_batch` (
  `id`            BIGINT       NOT NULL,
  `file_name`     VARCHAR(255)          DEFAULT NULL,
  `sheet_name`    VARCHAR(128)          DEFAULT NULL,
  `columns_json`  TEXT,
  `row_count`     INT                   DEFAULT 0,
  `importer_name` VARCHAR(64)           DEFAULT NULL,
  `remark`        VARCHAR(255)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次';

CREATE TABLE IF NOT EXISTS `import_row` (
  `id`        BIGINT NOT NULL,
  `batch_id`  BIGINT NOT NULL,
  `row_index` INT             DEFAULT NULL,
  `data_json` MEDIUMTEXT,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入数据行';

-- ---------- 业务员数据权限: 单据加 owner_id ----------
-- Keep this migration repeatable because fresh schemas may already contain these columns.
DROP PROCEDURE IF EXISTS ensure_owner_column;
DELIMITER $$
CREATE PROCEDURE ensure_owner_column(IN p_table VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = 'owner_id'
  ) THEN
    SET @statement = p_sql;
    PREPARE stmt FROM @statement;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$
DELIMITER ;

CALL ensure_owner_column('customer',
  'ALTER TABLE `customer` ADD COLUMN `owner_id` BIGINT DEFAULT NULL AFTER `id`');
CALL ensure_owner_column('sales_pi',
  'ALTER TABLE `sales_pi` ADD COLUMN `owner_id` BIGINT DEFAULT NULL AFTER `id`');
CALL ensure_owner_column('order_oem',
  'ALTER TABLE `order_oem` ADD COLUMN `owner_id` BIGINT DEFAULT NULL AFTER `id`');
DROP PROCEDURE IF EXISTS ensure_owner_column;

-- 演示数据归属到 admin, 便于管理员看到
UPDATE `customer`  SET `owner_id` = 1 WHERE `owner_id` IS NULL;
UPDATE `sales_pi`  SET `owner_id` = 1 WHERE `owner_id` IS NULL;
UPDATE `order_oem` SET `owner_id` = 1 WHERE `owner_id` IS NULL;

-- ---------- 菜单与权限 ----------
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  -- 资料共享 (顶级页)
  (700, 0,   '资料共享', 'fileshare:list',   'C', '/fileshare',  'fileshare/index',  'FolderOpened', 70, 1, NOW()),
  (701, 700, '上传文件', 'fileshare:upload', 'B', NULL, NULL, NULL, 1, 1, NOW()),
  (702, 700, '删除文件', 'fileshare:delete', 'B', NULL, NULL, NULL, 2, 1, NOW()),
  -- 报表 -> 业务员业绩 (perm_key=data:all 既是菜单权限也是"看全部"标志)
  (604, 600, '业务员业绩', 'data:all',       'C', '/report/salesman', 'report/salesman/index', 'User', 64, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ---------- 授权 ----------
-- 超管: 全部 (幂等)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 资料共享 list+upload(700,701) 给 业务员/销售主管/财务 (试运营: 只读新用户不预授资料共享)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT t.rid, t.pid FROM (
  SELECT r.id AS rid, p.id AS pid
  FROM `sys_role` r CROSS JOIN `sys_permission` p
  WHERE r.role_key IN ('salesman','sales_manager','finance')
    AND p.perm_key IN ('fileshare:list','fileshare:upload')
) t
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 删除文件(702) + 业务员业绩/看全部(604=data:all) 给 销售主管;  data:all 也给 财务
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT t.rid, t.pid FROM (
  SELECT r.id AS rid, p.id AS pid
  FROM `sys_role` r CROSS JOIN `sys_permission` p
  WHERE (r.role_key = 'sales_manager' AND p.perm_key IN ('fileshare:delete','data:all'))
     OR (r.role_key = 'finance' AND p.perm_key = 'data:all')
) t
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 业务员(salesman): 授予各业务模块全部菜单+按钮(基础资料/销售/订单/出货/财务/报表/资料共享),
-- 但不含 data:all(只看自己) / 系统管理。销售主管(sales_manager)同样授予这些业务操作权限。
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT t.rid, t.pid FROM (
  SELECT r.id AS rid, p.id AS pid
  FROM `sys_role` r CROSS JOIN `sys_permission` p
  WHERE r.role_key IN ('salesman','sales_manager')
    AND p.deleted = 0 AND p.status = 1
    AND p.id NOT IN (604)
    AND NOT (p.id BETWEEN 900 AND 999)
    AND NOT (p.parent_id BETWEEN 900 AND 999)
) t
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
