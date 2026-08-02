-- =====================================================================
-- 订单管理关联 PI + OEM 要求字段快照
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- 采购订单关联销售合同 PI
SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'pi_id'),
  'SELECT 1',
  'ALTER TABLE `purchase_order` ADD COLUMN `pi_id` BIGINT DEFAULT NULL COMMENT ''关联 PI ID'' AFTER `po_no`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'pi_no'),
  'SELECT 1',
  'ALTER TABLE `purchase_order` ADD COLUMN `pi_no` VARCHAR(64) DEFAULT NULL COMMENT ''关联 PI 单号快照'' AFTER `pi_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND INDEX_NAME = 'idx_purchase_order_pi'),
  'SELECT 1',
  'ALTER TABLE `purchase_order` ADD KEY `idx_purchase_order_pi` (`pi_id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- OEM 要求关联 PI, 并保存客户/产品快照
SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'pi_id'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `pi_id` BIGINT DEFAULT NULL COMMENT ''关联 PI ID'' AFTER `oem_no`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'pi_no'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `pi_no` VARCHAR(64) DEFAULT NULL COMMENT ''关联 PI 单号快照'' AFTER `pi_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'customer_name'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `customer_name` VARCHAR(255) DEFAULT NULL COMMENT ''客户名称快照'' AFTER `customer_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'product_model'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `product_model` VARCHAR(255) DEFAULT NULL COMMENT ''产品型号快照'' AFTER `product_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'factory_model'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `factory_model` VARCHAR(255) DEFAULT NULL COMMENT ''工厂型号快照'' AFTER `product_model`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND INDEX_NAME = 'idx_order_oem_pi'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD KEY `idx_order_oem_pi` (`pi_id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `order_oem_file` (
  `id`            BIGINT       NOT NULL,
  `oem_id`        BIGINT       NOT NULL COMMENT '所属 OEM 要求',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `stored_name`   VARCHAR(255) NOT NULL COMMENT '磁盘存储名',
  `content_type`  VARCHAR(128)          DEFAULT NULL,
  `size`          BIGINT                DEFAULT NULL COMMENT '字节大小',
  `uploader_name` VARCHAR(64)           DEFAULT NULL,
  `remark`        VARCHAR(255)          DEFAULT NULL,
  `create_by`     BIGINT      DEFAULT NULL,
  `create_time`   DATETIME    DEFAULT NULL,
  `update_by`     BIGINT      DEFAULT NULL,
  `update_time`   DATETIME    DEFAULT NULL,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order_oem_file_oem` (`oem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OEM 要求附件';

-- OEM 已取消导入和单据导出功能, 清理旧权限。
DELETE FROM `sys_role_permission`
WHERE `permission_id` IN (SELECT `id` FROM `sys_permission` WHERE `perm_key` IN ('order_oem:import', 'order_oem:export'));
DELETE FROM `sys_permission` WHERE `perm_key` IN ('order_oem:import', 'order_oem:export');
