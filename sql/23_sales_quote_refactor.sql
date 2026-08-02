-- 产品报价重构迁移
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND COLUMN_NAME = 'product_name'),
  'SELECT 1',
  'ALTER TABLE `standard_price` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL COMMENT ''产品名称'' AFTER `prod_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND COLUMN_NAME = 'company_model'),
  'SELECT 1',
  'ALTER TABLE `standard_price` ADD COLUMN `company_model` VARCHAR(255) DEFAULT NULL COMMENT ''公司产品型号'' AFTER `product_name`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND COLUMN_NAME = 'standard_unit_price'),
  'SELECT 1',
  'ALTER TABLE `standard_price` ADD COLUMN `standard_unit_price` DECIMAL(18,4) NULL COMMENT ''标准单价'' AFTER `price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND COLUMN_NAME = 'discount_unit_price'),
  'SELECT 1',
  'ALTER TABLE `standard_price` ADD COLUMN `discount_unit_price` DECIMAL(18,4) NULL COMMENT ''折扣单价'' AFTER `standard_unit_price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND COLUMN_NAME = 'floor_price'),
  'SELECT 1',
  'ALTER TABLE `standard_price` ADD COLUMN `floor_price` DECIMAL(18,4) NULL COMMENT ''底价, 默认折扣单价'' AFTER `discount_unit_price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `standard_price` sp
LEFT JOIN `product` p ON p.`id` = sp.`product_id` AND p.`deleted` = 0
SET
  sp.`product_name` = COALESCE(NULLIF(sp.`product_name`, ''), p.`product_name`, p.`prod_name_cn`),
  sp.`company_model` = COALESCE(NULLIF(sp.`company_model`, ''), p.`company_model`, p.`prod_code`),
  sp.`prod_code` = COALESCE(NULLIF(sp.`prod_code`, ''), p.`prod_code`),
  sp.`standard_unit_price` = COALESCE(sp.`standard_unit_price`, sp.`price`),
  sp.`discount_unit_price` = COALESCE(sp.`discount_unit_price`, sp.`price`),
  sp.`floor_price` = COALESCE(sp.`floor_price`, sp.`discount_unit_price`, sp.`price`),
  sp.`currency` = CASE
    WHEN sp.`currency` IS NULL OR sp.`currency` = '' THEN 'USD'
    ELSE UPPER(sp.`currency`)
  END;

ALTER TABLE `standard_price`
  MODIFY `product_id` BIGINT NOT NULL COMMENT '产品ID',
  MODIFY `prod_code` VARCHAR(64) DEFAULT NULL COMMENT '兼容旧数据: 产品代码',
  MODIFY `customer_id` BIGINT DEFAULT NULL COMMENT '兼容旧数据: 可空, 空为通用报价',
  MODIFY `price` DECIMAL(18,4) NOT NULL COMMENT '兼容旧数据: 系统单价/标准价',
  MODIFY `standard_unit_price` DECIMAL(18,4) NOT NULL COMMENT '标准单价',
  MODIFY `discount_unit_price` DECIMAL(18,4) NOT NULL COMMENT '折扣单价',
  MODIFY `floor_price` DECIMAL(18,4) DEFAULT NULL COMMENT '底价, 默认折扣单价',
  MODIFY `currency` VARCHAR(8) DEFAULT 'USD' COMMENT '币种',
  MODIFY `effective_date` DATE DEFAULT NULL COMMENT '兼容旧数据: 生效日期',
  COMMENT = '产品报价';

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'standard_price' AND INDEX_NAME = 'idx_standard_price_company_model'),
  'SELECT 1',
  'CREATE INDEX `idx_standard_price_company_model` ON `standard_price` (`company_model`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sys_permission`
SET `perm_name` = CASE `id`
  WHEN 201 THEN '产品报价'
  WHEN 2011 THEN '新增报价'
  WHEN 2012 THEN '修改报价'
  WHEN 2013 THEN '删除报价'
  WHEN 2014 THEN '导入报价'
  WHEN 2015 THEN '导出报价'
  ELSE `perm_name`
END
WHERE `id` IN (201, 2011, 2012, 2013, 2014, 2015);
