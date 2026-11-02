-- Fix repeated create/delete of the same username or role key (MySQL 8).
-- Select the target database before running this file; no business rows change.
-- Deleted rows get a NULL index component, allowing multiple history rows.
-- Keep the original string column in each index to preserve its collation.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 10;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'active_unique'),
  'SELECT ''sys_user.active_unique already exists'' AS migration',
  'ALTER TABLE `sys_user` ADD COLUMN `active_unique` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END) VIRTUAL COMMENT ''仅未删除记录参与唯一约束'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_username');
SET @index_non_unique = (SELECT MAX(non_unique) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_username');
-- Replace the index atomically: never leave active usernames unprotected.
SET @ddl = IF(@index_columns = 'username,active_unique' AND @index_non_unique = 0,
  'SELECT ''sys_user.uk_username already migrated'' AS migration',
  CONCAT('ALTER TABLE `sys_user` ', IF(@index_columns IS NULL, '', 'DROP INDEX `uk_username`, '),
         'ADD UNIQUE KEY `uk_username` (`username`, `active_unique`)'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'active_unique'),
  'SELECT ''sys_role.active_unique already exists'' AS migration',
  'ALTER TABLE `sys_role` ADD COLUMN `active_unique` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END) VIRTUAL COMMENT ''仅未删除记录参与唯一约束'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND index_name = 'uk_role_key');
SET @index_non_unique = (SELECT MAX(non_unique) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND index_name = 'uk_role_key');
SET @ddl = IF(@index_columns = 'role_key,active_unique' AND @index_non_unique = 0,
  'SELECT ''sys_role.uk_role_key already migrated'' AS migration',
  CONCAT('ALTER TABLE `sys_role` ', IF(@index_columns IS NULL, '', 'DROP INDEX `uk_role_key`, '),
         'ADD UNIQUE KEY `uk_role_key` (`role_key`, `active_unique`)'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
