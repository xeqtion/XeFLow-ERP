-- 销售报价重构迁移
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_price' AND COLUMN_NAME = 'customer_name'),
  'SELECT 1',
  'ALTER TABLE `contract_price` ADD COLUMN `customer_name` VARCHAR(255) DEFAULT NULL COMMENT ''客户名称'' AFTER `customer_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_price' AND COLUMN_NAME = 'product_name'),
  'SELECT 1',
  'ALTER TABLE `contract_price` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL COMMENT ''产品名称'' AFTER `product_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_price' AND COLUMN_NAME = 'product_model'),
  'SELECT 1',
  'ALTER TABLE `contract_price` ADD COLUMN `product_model` VARCHAR(255) DEFAULT NULL COMMENT ''产品型号'' AFTER `product_name`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `contract_price` cp
LEFT JOIN `product` p ON p.`id` = cp.`product_id` AND p.`deleted` = 0
LEFT JOIN `customer` c ON c.`id` = cp.`customer_id` AND c.`deleted` = 0
SET
  cp.`product_name` = COALESCE(NULLIF(cp.`product_name`, ''), p.`product_name`, p.`prod_name_cn`, p.`prod_name_en`),
  cp.`product_model` = COALESCE(NULLIF(cp.`product_model`, ''), p.`company_model`, p.`prod_code`, p.`spec`),
  cp.`prod_code` = COALESCE(NULLIF(cp.`prod_code`, ''), p.`prod_code`),
  cp.`customer_name` = COALESCE(NULLIF(cp.`customer_name`, ''), c.`company_name`, c.`cust_name_en`, c.`cust_name_cn`, c.`cust_code`),
  cp.`currency` = CASE
    WHEN cp.`currency` IS NULL OR cp.`currency` = '' THEN 'USD'
    ELSE UPPER(cp.`currency`)
  END;

ALTER TABLE `contract_price`
  MODIFY `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  MODIFY `product_id` BIGINT NOT NULL COMMENT '产品ID',
  MODIFY `prod_code` VARCHAR(64) DEFAULT NULL COMMENT '兼容旧数据: 产品代码',
  MODIFY `standard_price` DECIMAL(18,4) DEFAULT NULL COMMENT '产品报价标准单价快照',
  MODIFY `contract_price` DECIMAL(18,4) NOT NULL COMMENT '成交单价',
  MODIFY `deviation` DECIMAL(18,6) DEFAULT NULL COMMENT '偏差率(基于标准价)',
  MODIFY `currency` VARCHAR(8) DEFAULT 'USD' COMMENT '币种',
  COMMENT = '销售报价(含审核)';

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_price' AND INDEX_NAME = 'idx_contract_price_product_model'),
  'SELECT 1',
  'CREATE INDEX `idx_contract_price_product_model` ON `contract_price` (`product_model`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_price' AND INDEX_NAME = 'idx_contract_price_customer_name'),
  'SELECT 1',
  'CREATE INDEX `idx_contract_price_customer_name` ON `contract_price` (`customer_name`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `contract_price` cp
LEFT JOIN `standard_price` sp ON sp.`id` = (
  SELECT sp2.`id`
  FROM `standard_price` sp2
  WHERE sp2.`product_id` = cp.`product_id`
    AND sp2.`deleted` = 0
    AND (sp2.`customer_id` = cp.`customer_id` OR sp2.`customer_id` IS NULL)
  ORDER BY (sp2.`customer_id` IS NULL) ASC, sp2.`id` DESC
  LIMIT 1
)
SET
  cp.`standard_price` = sp.`standard_unit_price`,
  cp.`deviation` = CASE
    WHEN sp.`standard_unit_price` IS NULL OR sp.`discount_unit_price` IS NULL
      OR sp.`standard_unit_price` = 0 THEN NULL
    ELSE ROUND((cp.`contract_price` - sp.`standard_unit_price`) / sp.`standard_unit_price`, 6)
  END
WHERE cp.`deleted` = 0;

UPDATE `sys_permission`
SET `perm_name` = CASE `id`
  WHEN 202 THEN '销售报价'
  WHEN 2021 THEN '报价审核'
  WHEN 2022 THEN '新增报价'
  WHEN 2023 THEN '修改报价'
  WHEN 2024 THEN '删除报价'
  ELSE `perm_name`
END
WHERE `id` IN (202, 2021, 2022, 2023, 2024);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, 2021
FROM `sys_role` r
WHERE r.`role_key` IN ('admin', 'sales_manager');
