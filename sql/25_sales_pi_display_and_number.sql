-- 销售合同PI列表展示与自动编号支持
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi' AND COLUMN_NAME = 'payment_method'),
  'SELECT 1',
  'ALTER TABLE `sales_pi` ADD COLUMN `payment_method` VARCHAR(128) DEFAULT NULL COMMENT ''付款方式'' AFTER `sign_date`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi' AND COLUMN_NAME = 'trade_term'),
  'SELECT 1',
  'ALTER TABLE `sales_pi` ADD COLUMN `trade_term` VARCHAR(128) DEFAULT NULL COMMENT ''贸易条款'' AFTER `payment_method`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi' AND COLUMN_NAME = 'delivery_date'),
  'SELECT 1',
  'ALTER TABLE `sales_pi` ADD COLUMN `delivery_date` DATE DEFAULT NULL COMMENT ''交货日期'' AFTER `trade_term`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi' AND COLUMN_NAME = 'dest_port'),
  'SELECT 1',
  'ALTER TABLE `sales_pi` ADD COLUMN `dest_port` VARCHAR(128) DEFAULT NULL COMMENT ''目的港'' AFTER `delivery_date`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi' AND COLUMN_NAME = 'shipping_marks'),
  'SELECT 1',
  'ALTER TABLE `sales_pi` ADD COLUMN `shipping_marks` VARCHAR(512) DEFAULT NULL COMMENT ''唛头'' AFTER `dest_port`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
