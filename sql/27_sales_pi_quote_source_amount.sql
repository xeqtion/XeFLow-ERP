-- 销售合同PI明细报价来源与手动金额支持
SET NAMES utf8mb4;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_pi_item' AND COLUMN_NAME = 'quote_source'),
  'SELECT 1',
  'ALTER TABLE `sales_pi_item` ADD COLUMN `quote_source` VARCHAR(32) DEFAULT NULL COMMENT ''报价来源: SALES_QUOTE/STANDARD_PRICE/DISCOUNT_PRICE/FLOOR_PRICE, 空为手动'' AFTER `quote_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sales_pi_item`
   SET `quote_source` = 'SALES_QUOTE'
 WHERE `quote_id` IS NOT NULL
   AND (`quote_source` IS NULL OR `quote_source` = '');
