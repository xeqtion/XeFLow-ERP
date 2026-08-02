-- Add file format and expiry metadata to shared files.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_shared_file_column $$
CREATE PROCEDURE add_shared_file_column(
  IN p_column_name VARCHAR(64),
  IN p_ddl TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'shared_file'
       AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = p_ddl;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

CALL add_shared_file_column('file_format',
  'ALTER TABLE `shared_file` ADD COLUMN `file_format` VARCHAR(32) DEFAULT NULL AFTER `content_type`') $$
CALL add_shared_file_column('expire_time',
  'ALTER TABLE `shared_file` ADD COLUMN `expire_time` DATETIME DEFAULT NULL COMMENT ''过期时间'' AFTER `remark`') $$

DROP PROCEDURE IF EXISTS add_shared_file_column $$

DELIMITER ;

ALTER TABLE `shared_file`
  MODIFY `category` VARCHAR(64) DEFAULT '无';

UPDATE shared_file
   SET category = '无'
 WHERE deleted = 0
   AND (category IS NULL OR category = '' OR category = '未分类');

UPDATE shared_file
   SET file_format = UPPER(SUBSTRING_INDEX(file_name, '.', -1))
 WHERE deleted = 0
   AND (file_format IS NULL OR file_format = '')
   AND file_name LIKE '%.%'
   AND SUBSTRING_INDEX(file_name, '.', -1) <> file_name;

UPDATE shared_file
   SET file_format = '无'
 WHERE deleted = 0
   AND (file_format IS NULL OR file_format = '');

UPDATE shared_file
   SET expire_time = DATE_ADD(COALESCE(create_time, NOW()), INTERVAL 180 DAY)
 WHERE deleted = 0
   AND expire_time IS NULL;
