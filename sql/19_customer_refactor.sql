-- 客户表重构：业务客户ID、主界面字段、详情扩展键值

DELIMITER $$

DROP PROCEDURE IF EXISTS add_customer_column $$
CREATE PROCEDURE add_customer_column(IN p_column VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'customer'
       AND column_name = p_column
  ) THEN
    SET @ddl = p_sql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

CALL add_customer_column('contact_name',
  'ALTER TABLE `customer` ADD COLUMN `contact_name` VARCHAR(128) DEFAULT NULL COMMENT ''联系人姓名'' AFTER `cust_code`');
CALL add_customer_column('contact_position',
  'ALTER TABLE `customer` ADD COLUMN `contact_position` VARCHAR(128) DEFAULT NULL COMMENT ''职位'' AFTER `contact_name`');
CALL add_customer_column('company_name',
  'ALTER TABLE `customer` ADD COLUMN `company_name` VARCHAR(255) DEFAULT NULL COMMENT ''公司名称'' AFTER `country`');
CALL add_customer_column('website',
  'ALTER TABLE `customer` ADD COLUMN `website` VARCHAR(255) DEFAULT NULL COMMENT ''网站'' AFTER `currency`');
CALL add_customer_column('social_contact',
  'ALTER TABLE `customer` ADD COLUMN `social_contact` VARCHAR(255) DEFAULT NULL COMMENT ''社媒联系'' AFTER `email`');

DROP PROCEDURE IF EXISTS add_customer_column;

CREATE TABLE IF NOT EXISTS `customer_extra_field` (
  `id`          BIGINT       NOT NULL,
  `customer_id` BIGINT       NOT NULL COMMENT '客户内部ID',
  `field_key`   VARCHAR(128) NOT NULL COMMENT '字段名',
  `field_value` VARCHAR(1024)         DEFAULT NULL COMMENT '字段值',
  `sort`        INT                   DEFAULT 0 COMMENT '排序',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_customer_extra_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户扩展键值资料';

UPDATE `customer`
   SET `company_name` = COALESCE(NULLIF(`company_name`, ''), NULLIF(`cust_name_cn`, ''), NULLIF(`cust_name_en`, '')),
       `contact_name` = COALESCE(NULLIF(`contact_name`, ''), NULLIF(`contact`, '')),
       `currency` = COALESCE(NULLIF(`currency`, ''), 'USD')
 WHERE `deleted` = 0;
