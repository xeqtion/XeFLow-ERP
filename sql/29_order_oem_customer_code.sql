-- =====================================================================
-- OEM 要求显示客户资料中的业务客户ID (如 AU9104)
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_oem' AND COLUMN_NAME = 'customer_code'),
  'SELECT 1',
  'ALTER TABLE `order_oem` ADD COLUMN `customer_code` VARCHAR(64) DEFAULT NULL COMMENT ''客户业务ID快照'' AFTER `customer_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `order_oem` o
JOIN `customer` c ON c.`id` = o.`customer_id` AND c.`deleted` = 0
SET o.`customer_code` = c.`cust_code`
WHERE (o.`customer_code` IS NULL OR o.`customer_code` = '');
