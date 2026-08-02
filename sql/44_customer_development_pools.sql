-- =====================================================================
-- Customer development pools
-- PERSONAL: 已确认唯一跟单业务员的个人发展客户池
-- PUBLIC:   未确认担当的公共发展客户池
-- Run manually for an existing database:
-- mysql -u root -p erp < sql/44_customer_development_pools.sql
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ensure_customer_pool_column;
DELIMITER $$
CREATE PROCEDURE ensure_customer_pool_column(IN p_column VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer' AND COLUMN_NAME = p_column
  ) THEN
    SET @statement = p_sql;
    PREPARE stmt FROM @statement;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$
DELIMITER ;

CALL ensure_customer_pool_column('owner_id',
  'ALTER TABLE `customer` ADD COLUMN `owner_id` BIGINT DEFAULT NULL AFTER `id`');
CALL ensure_customer_pool_column('pool_type',
  'ALTER TABLE `customer` ADD COLUMN `pool_type` VARCHAR(16) NOT NULL DEFAULT ''PUBLIC'' AFTER `owner_id`');
CALL ensure_customer_pool_column('owner_assigned_at',
  'ALTER TABLE `customer` ADD COLUMN `owner_assigned_at` DATETIME DEFAULT NULL AFTER `pool_type`');
DROP PROCEDURE IF EXISTS ensure_customer_pool_column;

-- Existing assigned customers keep their responsible salesman. Existing unassigned customers enter public pool.
UPDATE `customer`
SET `pool_type` = CASE WHEN `owner_id` IS NULL THEN 'PUBLIC' ELSE 'PERSONAL' END
WHERE `pool_type` IS NULL OR `pool_type` NOT IN ('PERSONAL', 'PUBLIC');

UPDATE `customer`
SET `owner_assigned_at` = COALESCE(`owner_assigned_at`, `update_time`, `create_time`, NOW())
WHERE `pool_type` = 'PERSONAL' AND `owner_id` IS NOT NULL;

UPDATE `customer`
SET `owner_id` = NULL,
    `owner_assigned_at` = NULL
WHERE `pool_type` = 'PUBLIC';

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer' AND INDEX_NAME = 'idx_customer_pool_owner'
);
SET @idx_sql = IF(@idx_exists = 0,
  'CREATE INDEX `idx_customer_pool_owner` ON `customer` (`pool_type`, `owner_id`)',
  'SELECT 1');
PREPARE idx_stmt FROM @idx_sql;
EXECUTE idx_stmt;
DEALLOCATE PREPARE idx_stmt;
