-- 产品参数价格 -> 产品资料

DELIMITER $$

DROP PROCEDURE IF EXISTS add_product_column $$
CREATE PROCEDURE add_product_column(IN p_column VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'product'
       AND column_name = p_column
  ) THEN
    SET @ddl = p_sql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

CALL add_product_column('product_name',
  'ALTER TABLE `product` ADD COLUMN `product_name` VARCHAR(255) DEFAULT NULL COMMENT ''产品名称'' AFTER `prod_code`');
CALL add_product_column('company_model',
  'ALTER TABLE `product` ADD COLUMN `company_model` VARCHAR(255) DEFAULT NULL COMMENT ''公司产品型号'' AFTER `product_name`');
CALL add_product_column('factory_model',
  'ALTER TABLE `product` ADD COLUMN `factory_model` VARCHAR(255) DEFAULT NULL COMMENT ''工厂产品型号'' AFTER `company_model`');
CALL add_product_column('factory_price',
  'ALTER TABLE `product` ADD COLUMN `factory_price` DECIMAL(18,4) DEFAULT NULL COMMENT ''出厂价'' AFTER `factory_model`');

DROP PROCEDURE IF EXISTS add_product_column;

UPDATE `product`
   SET `product_name` = COALESCE(NULLIF(`product_name`, ''), NULLIF(`prod_name_cn`, ''), NULLIF(`prod_name_en`, '')),
       `company_model` = COALESCE(NULLIF(`company_model`, ''), NULLIF(`spec`, '')),
       `factory_model` = COALESCE(NULLIF(`factory_model`, ''), NULLIF(`prod_code`, '')),
       `factory_price` = COALESCE(`factory_price`, `cost_price`),
       `currency` = COALESCE(NULLIF(`currency`, ''), 'CNY')
 WHERE `deleted` = 0;

ALTER TABLE `product` COMMENT = '产品资料';

UPDATE `sys_permission`
   SET `perm_name` = '产品资料'
 WHERE `id` = 102
    OR `perm_key` = 'product:list'
    OR `perm_name` = '产品参数价格';
