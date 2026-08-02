-- Procurement ownership, product snapshots and final shipment status normalization.
USE `erp`;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ensure_procurement_column;
DELIMITER $$
CREATE PROCEDURE ensure_procurement_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @statement = p_sql;
    PREPARE stmt FROM @statement;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$
DELIMITER ;

CALL ensure_procurement_column('purchase_order', 'purchaser_id',
  'ALTER TABLE `purchase_order` ADD COLUMN `purchaser_id` BIGINT DEFAULT NULL AFTER `pi_no`');
CALL ensure_procurement_column('purchase_order_item', 'product_name',
  'ALTER TABLE `purchase_order_item` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL AFTER `prod_code`');
CALL ensure_procurement_column('stock_movement', 'product_name',
  'ALTER TABLE `stock_movement` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL AFTER `product_id`');
DROP PROCEDURE IF EXISTS ensure_procurement_column;

UPDATE `shipment` SET `status` = 'PARTIAL_SHIPPED'
WHERE `status` IN ('DRAFT', 'CONFIRMED');
UPDATE `shipment` SET `status` = 'FULL_SHIPPED'
WHERE `status` IN ('SHIPPED', 'DELIVERED', 'COMPLETED');
