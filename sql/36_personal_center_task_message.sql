-- Personal center, automatic calendar tasks, and message retention support.
USE `erp`;
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_personal_task_column $$
CREATE PROCEDURE add_personal_task_column(IN p_column VARCHAR(64), IN p_sql TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'personal_task'
       AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = p_sql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

CALL add_personal_task_column('category',
  'ALTER TABLE `personal_task` ADD COLUMN `category` VARCHAR(64) DEFAULT ''MANUAL'' COMMENT ''事项分类'' AFTER `priority`');
CALL add_personal_task_column('source_type',
  'ALTER TABLE `personal_task` ADD COLUMN `source_type` VARCHAR(64) DEFAULT NULL COMMENT ''来源业务类型'' AFTER `category`');
CALL add_personal_task_column('source_id',
  'ALTER TABLE `personal_task` ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT ''来源业务ID'' AFTER `source_type`');
CALL add_personal_task_column('source_no',
  'ALTER TABLE `personal_task` ADD COLUMN `source_no` VARCHAR(128) DEFAULT NULL COMMENT ''来源业务单号'' AFTER `source_id`');
CALL add_personal_task_column('system_generated',
  'ALTER TABLE `personal_task` ADD COLUMN `system_generated` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否系统自动生成'' AFTER `source_no`');

DROP PROCEDURE IF EXISTS add_personal_task_column;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'personal_task' AND INDEX_NAME = 'idx_task_source'),
  'SELECT 1',
  'ALTER TABLE `personal_task` ADD INDEX `idx_task_source` (`owner_id`, `source_type`, `source_id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `personal_task`
   SET `category` = COALESCE(NULLIF(`category`, ''), 'MANUAL'),
       `system_generated` = COALESCE(`system_generated`, 0)
 WHERE `deleted` = 0;

UPDATE `sys_user`
   SET `nickname` = COALESCE(NULLIF(`nickname`, ''), NULLIF(`real_name`, ''), `username`)
 WHERE `deleted` = 0;
