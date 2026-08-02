-- Optional Chinese display name for exchange-rate records.
-- Safe to run repeatedly on an existing database.
USE `erp`;
SET NAMES utf8mb4;

SET @currency_name_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'currency_rate'
    AND COLUMN_NAME = 'currency_name'
);
SET @currency_name_sql = IF(
  @currency_name_exists = 0,
  'ALTER TABLE `currency_rate` ADD COLUMN `currency_name` VARCHAR(64) DEFAULT NULL AFTER `currency`',
  'SELECT 1'
);
PREPARE currency_name_stmt FROM @currency_name_sql;
EXECUTE currency_name_stmt;
DEALLOCATE PREPARE currency_name_stmt;

UPDATE `currency_rate`
SET `currency_name` = CASE `currency`
  WHEN 'USD' THEN '美元'
  WHEN 'EUR' THEN '欧元'
  WHEN 'HKD' THEN '港币'
  WHEN 'JPY' THEN '日元'
  WHEN 'GBP' THEN '英镑'
  WHEN 'SGD' THEN '新加坡元'
  WHEN 'RUB' THEN '卢布'
  WHEN 'TWD' THEN '新台币'
  ELSE `currency_name`
END
WHERE `currency_name` IS NULL OR TRIM(`currency_name`) = '';
