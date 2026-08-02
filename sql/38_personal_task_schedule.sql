-- Personal task schedule fields for day agenda planning.
-- Run manually against local/dev database when upgrading an existing schema.
USE `erp`;
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_personal_task_schedule_column $$
CREATE PROCEDURE add_personal_task_schedule_column(IN p_column VARCHAR(64), IN p_sql TEXT)
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

CALL add_personal_task_schedule_column('all_day',
  'ALTER TABLE `personal_task` ADD COLUMN `all_day` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否全天'' AFTER `task_date`');
CALL add_personal_task_schedule_column('start_time',
  'ALTER TABLE `personal_task` ADD COLUMN `start_time` TIME DEFAULT NULL COMMENT ''开始时间'' AFTER `all_day`');
CALL add_personal_task_schedule_column('end_time',
  'ALTER TABLE `personal_task` ADD COLUMN `end_time` TIME DEFAULT NULL COMMENT ''结束时间'' AFTER `start_time`');
CALL add_personal_task_schedule_column('location',
  'ALTER TABLE `personal_task` ADD COLUMN `location` VARCHAR(255) DEFAULT NULL COMMENT ''地点'' AFTER `end_time`');

DROP PROCEDURE IF EXISTS add_personal_task_schedule_column;

UPDATE `personal_task`
   SET `all_day` = 1
 WHERE `all_day` IS NULL;
