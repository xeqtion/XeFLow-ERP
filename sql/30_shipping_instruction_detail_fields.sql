DELIMITER $$

DROP PROCEDURE IF EXISTS add_shipping_instruction_column $$
CREATE PROCEDURE add_shipping_instruction_column(
  IN p_column VARCHAR(64),
  IN p_sql TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shipping_instruction'
      AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = p_sql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

CALL add_shipping_instruction_column('shipping_address',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `shipping_address` VARCHAR(512) DEFAULT NULL AFTER `ship_date`');
CALL add_shipping_instruction_column('delivery_address',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `delivery_address` VARCHAR(512) DEFAULT NULL AFTER `shipping_address`');
CALL add_shipping_instruction_column('contact_name',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `contact_name` VARCHAR(128) DEFAULT NULL AFTER `delivery_address`');
CALL add_shipping_instruction_column('phone',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `phone` VARCHAR(64) DEFAULT NULL AFTER `contact_name`');
CALL add_shipping_instruction_column('order_no',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `order_no` VARCHAR(128) DEFAULT NULL AFTER `phone`');
CALL add_shipping_instruction_column('order_country',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `order_country` VARCHAR(128) DEFAULT NULL AFTER `order_no`');
CALL add_shipping_instruction_column('product_model',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `product_model` VARCHAR(512) DEFAULT NULL AFTER `order_country`');
CALL add_shipping_instruction_column('factory_model',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `factory_model` VARCHAR(512) DEFAULT NULL AFTER `product_model`');
CALL add_shipping_instruction_column('quantity',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `quantity` DECIMAL(18,4) DEFAULT NULL AFTER `factory_model`');
CALL add_shipping_instruction_column('carton_count',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `carton_count` DECIMAL(18,4) DEFAULT NULL AFTER `quantity`');
CALL add_shipping_instruction_column('order_requirement',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `order_requirement` TEXT DEFAULT NULL AFTER `carton_count`');
CALL add_shipping_instruction_column('owner_name',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `owner_name` VARCHAR(128) DEFAULT NULL AFTER `order_requirement`');
CALL add_shipping_instruction_column('order_date',
  'ALTER TABLE `shipping_instruction` ADD COLUMN `order_date` DATE DEFAULT NULL AFTER `owner_name`');

DROP PROCEDURE IF EXISTS add_shipping_instruction_column;
