-- =====================================================================
-- 公告分组键: 同一次广播的所有副本共享 group_key, 便于管理员整条删除
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/17_notification_group.sql"
-- 若列已存在会报 Duplicate column, 可安全忽略
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

ALTER TABLE `sys_notification` ADD COLUMN `group_key` VARCHAR(64) DEFAULT NULL COMMENT '公告分组(广播批次)' AFTER `type`;

-- 旧公告按 标题+内容 归并出一个分组键, 让管理员也能整条删除历史公告
UPDATE `sys_notification`
SET `group_key` = MD5(CONCAT(IFNULL(`title`,''), '|', IFNULL(`content`,'')))
WHERE `type` = 'ANNOUNCE' AND `group_key` IS NULL;
