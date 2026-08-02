-- =====================================================================
-- 站内通知 + 周报月报 - 通知表 与 公告/摘要发送权限
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 < sql/06_notification.sql
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_notification` (
  `id`           BIGINT       NOT NULL COMMENT '主键',
  `recipient_id` BIGINT       NOT NULL COMMENT '接收用户ID',
  `title`        VARCHAR(200) NOT NULL COMMENT '标题',
  `content`      TEXT                  COMMENT '正文(可含HTML)',
  `type`         VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM系统/REPORT报表/ANNOUNCE公告',
  `is_read`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `read_time`    DATETIME              DEFAULT NULL,
  `create_by`    BIGINT                DEFAULT NULL,
  `create_time`  DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

-- 发送公告 / 发送报表摘要 权限 (挂在 系统管理 900 下, 仅按钮)
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (9061, 900, '发送通知公告', 'system:notification:send', 'B', 61, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- 超级管理员角色 -> 全部权限 (含上面新增项, 幂等)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
