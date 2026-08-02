-- 产品清单BOM -> 产品配件资料
-- 物料编码格式: MT + 4位数字, 例如 MT0001。
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'support_models'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `support_models` VARCHAR(1024) DEFAULT NULL COMMENT ''支持产品型号(公司产品型号), 多个逗号分隔, 所有产品通用填所有'' AFTER `version`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'universal'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `universal` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否通用: 1是0否'' AFTER `support_models`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'spec'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `spec` VARCHAR(255) DEFAULT NULL COMMENT ''规格'' AFTER `universal`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'qty'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `qty` DECIMAL(18,4) DEFAULT NULL COMMENT ''用量'' AFTER `spec`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'supplier'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `supplier` VARCHAR(255) DEFAULT NULL COMMENT ''供应商'' AFTER `qty`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'supplier_phone'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `supplier_phone` VARCHAR(64) DEFAULT NULL COMMENT ''联系电话'' AFTER `supplier`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'supplier_contact'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `supplier_contact` VARCHAR(128) DEFAULT NULL COMMENT ''联系人'' AFTER `supplier_phone`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'unit_price'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `unit_price` DECIMAL(18,4) DEFAULT NULL COMMENT ''单价'' AFTER `supplier_contact`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bom' AND COLUMN_NAME = 'currency'),
  'SELECT 1',
  'ALTER TABLE `bom` ADD COLUMN `currency` VARCHAR(8) DEFAULT ''CNY'' COMMENT ''币种'' AFTER `unit_price`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `bom`
  MODIFY `bom_code` VARCHAR(64) NOT NULL COMMENT '物料编码(MT0001)',
  MODIFY `prod_code` VARCHAR(64) DEFAULT NULL COMMENT '兼容旧数据: 产品代码',
  MODIFY `name_cn` VARCHAR(255) DEFAULT NULL COMMENT '名称',
  MODIFY `name_en` VARCHAR(255) DEFAULT NULL COMMENT '兼容旧数据: 英文名',
  MODIFY `version` VARCHAR(32) DEFAULT NULL COMMENT '兼容旧数据: 版本',
  MODIFY `currency` VARCHAR(8) DEFAULT 'CNY' COMMENT '币种',
  COMMENT = '产品配件资料';

UPDATE `sys_permission`
SET `perm_name` = CASE `id`
  WHEN 103 THEN '产品配件资料'
  WHEN 1031 THEN '新增配件'
  WHEN 1032 THEN '修改配件'
  WHEN 1033 THEN '删除配件'
  WHEN 1034 THEN '导入配件'
  WHEN 1035 THEN '导出配件'
  ELSE `perm_name`
END
WHERE `id` IN (103, 1031, 1032, 1033, 1034, 1035);

SET @mt_seq := (
  SELECT IFNULL(MAX(CAST(SUBSTRING(`bom_code`, 3) AS UNSIGNED)), 0)
  FROM `bom`
  WHERE `bom_code` REGEXP '^MT[0-9]{4,}$'
);

CREATE TEMPORARY TABLE `tmp_bom_item_migration` AS
SELECT
  bi.`id`,
  CONCAT('MT', LPAD(@mt_seq := @mt_seq + 1, 4, '0')) AS `bom_code`,
  b.`product_id`,
  b.`prod_code`,
  COALESCE(NULLIF(bi.`name_cn`, ''), bi.`material_model`) AS `name_cn`,
  bi.`name_en`,
  b.`version`,
  CASE
    WHEN b.`prod_code` IS NULL OR b.`prod_code` = '' THEN '所有'
    ELSE b.`prod_code`
  END AS `support_models`,
  CASE
    WHEN b.`prod_code` IS NULL OR b.`prod_code` = '' THEN 1
    ELSE 0
  END AS `universal`,
  bi.`material_model` AS `spec`,
  bi.`qty`,
  bi.`unit_price`,
  COALESCE(NULLIF(bi.`currency`, ''), 'CNY') AS `currency`,
  b.`remark`,
  bi.`create_by`,
  COALESCE(bi.`create_time`, NOW()) AS `create_time`,
  bi.`update_by`,
  bi.`update_time`,
  0 AS `deleted`
FROM `bom_item` bi
JOIN `bom` b ON b.`id` = bi.`bom_id` AND b.`deleted` = 0
WHERE bi.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `bom` existing WHERE existing.`id` = bi.`id`);

INSERT INTO `bom` (
  `id`, `bom_code`, `product_id`, `prod_code`, `name_cn`, `name_en`, `version`,
  `support_models`, `universal`, `spec`, `qty`, `unit_price`, `remark`,
  `currency`,
  `create_by`, `create_time`, `update_by`, `update_time`, `deleted`
)
SELECT
  `id`, `bom_code`, `product_id`, `prod_code`, `name_cn`, `name_en`, `version`,
  `support_models`, `universal`, `spec`, `qty`, `unit_price`, `remark`,
  `currency`,
  `create_by`, `create_time`, `update_by`, `update_time`, `deleted`
FROM `tmp_bom_item_migration`;

UPDATE `bom` b
SET b.`deleted` = 1
WHERE b.`deleted` = 0
  AND b.`bom_code` NOT REGEXP '^MT[0-9]{4,}$'
  AND EXISTS (
    SELECT 1
    FROM `bom_item` bi
    WHERE bi.`bom_id` = b.`id` AND bi.`deleted` = 0
  );

CREATE TEMPORARY TABLE `tmp_bom_parent_codes` AS
SELECT
  b.`id`,
  CONCAT('MT', LPAD(@mt_seq := @mt_seq + 1, 4, '0')) AS `bom_code`
FROM `bom` b
WHERE b.`deleted` = 0
  AND b.`bom_code` NOT REGEXP '^MT[0-9]{4,}$';

UPDATE `bom` b
JOIN `tmp_bom_parent_codes` t ON t.`id` = b.`id`
SET
  b.`bom_code` = t.`bom_code`,
  b.`support_models` = CASE
    WHEN b.`prod_code` IS NULL OR b.`prod_code` = '' THEN '所有'
    ELSE b.`prod_code`
  END,
  b.`universal` = CASE
    WHEN b.`prod_code` IS NULL OR b.`prod_code` = '' THEN 1
    ELSE 0
  END;

UPDATE `bom`
SET
  `support_models` = CASE
    WHEN `support_models` IS NULL OR `support_models` = '' THEN '所有'
    ELSE `support_models`
  END,
  `universal` = CASE
    WHEN `support_models` = '所有' THEN 1
    ELSE `universal`
  END,
  `currency` = CASE
    WHEN `currency` IS NULL OR `currency` = '' THEN 'CNY'
    ELSE UPPER(`currency`)
  END
WHERE `deleted` = 0;

DROP TEMPORARY TABLE IF EXISTS `tmp_bom_item_migration`;
DROP TEMPORARY TABLE IF EXISTS `tmp_bom_parent_codes`;
