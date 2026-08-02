-- PO confirmation/partial receipt and inventory-driven partial shipment.
-- Run once for an existing local or deployed database.
USE `erp`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `shipment_item` (
  `id`          BIGINT        NOT NULL,
  `shipment_id` BIGINT        NOT NULL,
  `pi_item_id`  BIGINT                 DEFAULT NULL,
  `product_id`  BIGINT                 DEFAULT NULL,
  `prod_code`   VARCHAR(64)            DEFAULT NULL,
  `qty`         DECIMAL(18,4) NOT NULL DEFAULT 0,
  `create_by`   BIGINT                 DEFAULT NULL,
  `create_time` DATETIME               DEFAULT NULL,
  `update_by`   BIGINT                 DEFAULT NULL,
  `update_time` DATETIME               DEFAULT NULL,
  `deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_shipment_item_shipment` (`shipment_id`),
  KEY `idx_shipment_item_pi` (`pi_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货单产品明细';

-- MySQL releases differ in support for ADD COLUMN IF NOT EXISTS. Use a
-- metadata check so this migration remains repeatable across supported versions.
DROP PROCEDURE IF EXISTS ensure_po_receipt_column;
DELIMITER $$
CREATE PROCEDURE ensure_po_receipt_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_sql TEXT)
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

CALL ensure_po_receipt_column('purchase_order_item', 'received_qty',
  'ALTER TABLE `purchase_order_item` ADD COLUMN `received_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER `qty`');
CALL ensure_po_receipt_column('shipment_item', 'pi_item_id',
  'ALTER TABLE `shipment_item` ADD COLUMN `pi_item_id` BIGINT DEFAULT NULL AFTER `shipment_id`');
CALL ensure_po_receipt_column('shipment_item', 'product_id',
  'ALTER TABLE `shipment_item` ADD COLUMN `product_id` BIGINT DEFAULT NULL AFTER `pi_item_id`');
CALL ensure_po_receipt_column('shipment_item', 'prod_code',
  'ALTER TABLE `shipment_item` ADD COLUMN `prod_code` VARCHAR(64) DEFAULT NULL AFTER `product_id`');
DROP PROCEDURE IF EXISTS ensure_po_receipt_column;

-- Preserve historical terminology while using the new completed status in the UI and workflow.
UPDATE `purchase_order` SET `status` = 'COMPLETED' WHERE `status` = 'RECEIVED';
