-- 销售合同PI明细快照与单据追踪展示支持
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'product_name'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL COMMENT ''产品名称快照'' AFTER `prod_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'product_model'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `product_model` VARCHAR(255) DEFAULT NULL COMMENT ''产品型号快照'' AFTER `product_name`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'factory_model'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `factory_model` VARCHAR(255) DEFAULT NULL COMMENT ''工厂产品型号快照'' AFTER `product_model`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'unit'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `unit` VARCHAR(32) DEFAULT NULL COMMENT ''单位'' AFTER `factory_model`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'quote_id'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `quote_id` BIGINT DEFAULT NULL COMMENT ''关联销售报价'' AFTER `unit`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'standard_price'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `standard_price` DECIMAL(18,4) DEFAULT NULL COMMENT ''产品报价标准单价快照'' AFTER `quote_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'discount_unit_price'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `discount_unit_price` DECIMAL(18,4) DEFAULT NULL COMMENT ''产品报价折扣单价快照'' AFTER `standard_price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'floor_price'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `floor_price` DECIMAL(18,4) DEFAULT NULL COMMENT ''产品报价底价快照'' AFTER `discount_unit_price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'remark'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `remark` VARCHAR(512) DEFAULT NULL COMMENT ''明细备注'' AFTER `amount`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND INDEX_NAME = 'idx_sales_pi_item_quote'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD KEY `idx_sales_pi_item_quote` (`quote_id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sales_pi_item` i
JOIN `product` p ON p.`id` = i.`product_id`
   SET i.`product_name` = COALESCE(NULLIF(i.`product_name`, ''), p.`product_name`, p.`prod_name_cn`, p.`prod_name_en`),
       i.`product_model` = COALESCE(NULLIF(i.`product_model`, ''), p.`company_model`, p.`prod_code`, p.`spec`),
       i.`factory_model` = COALESCE(NULLIF(i.`factory_model`, ''), p.`factory_model`),
       i.`unit` = COALESCE(NULLIF(i.`unit`, ''), p.`unit`),
       i.`prod_code` = COALESCE(NULLIF(i.`prod_code`, ''), p.`prod_code`)
 WHERE i.`product_id` IS NOT NULL;
