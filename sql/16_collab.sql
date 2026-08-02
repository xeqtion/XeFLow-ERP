-- =====================================================================
-- 协同办公增强: 客户附件 + 个人待办(首页日历) + 用户间消息(单聊+附件)
-- 加载(务必用 source, 避免 PowerShell 管道乱码):
--   mysql -u root -proot --default-character-set=utf8mb4 -e "source sql/16_collab.sql"
-- 表用 CREATE TABLE IF NOT EXISTS, 可重复执行
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- ---------- 客户附件 (客户详细资料文件: PDF/图片/表格等) ----------
CREATE TABLE IF NOT EXISTS `customer_file` (
  `id`            BIGINT       NOT NULL,
  `customer_id`   BIGINT       NOT NULL COMMENT '所属客户',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `stored_name`   VARCHAR(128) NOT NULL COMMENT '磁盘存储名',
  `content_type`  VARCHAR(128)          DEFAULT NULL,
  `size`          BIGINT                DEFAULT NULL,
  `uploader_name` VARCHAR(64)           DEFAULT NULL,
  `remark`        VARCHAR(255)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户附件';

-- ---------- 个人待办 / 首页日历计划 ----------
CREATE TABLE IF NOT EXISTS `personal_task` (
  `id`        BIGINT       NOT NULL,
  `owner_id`  BIGINT       NOT NULL COMMENT '归属用户',
  `title`     VARCHAR(255) NOT NULL COMMENT '标题',
  `content`   VARCHAR(1000)         DEFAULT NULL COMMENT '详情',
  `task_date` DATE                  DEFAULT NULL COMMENT '计划日期',
  `all_day`   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否全天',
  `start_time` TIME                 DEFAULT NULL COMMENT '开始时间',
  `end_time`   TIME                 DEFAULT NULL COMMENT '结束时间',
  `location`   VARCHAR(255)         DEFAULT NULL COMMENT '地点',
  `priority`  VARCHAR(16)           DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH',
  `status`    VARCHAR(16)           DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
  `done_time` DATETIME              DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_owner_date` (`owner_id`, `task_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人待办';

-- ---------- 用户间消息 (单聊 + 收发件箱) ----------
CREATE TABLE IF NOT EXISTS `user_message` (
  `id`               BIGINT NOT NULL,
  `sender_id`        BIGINT NOT NULL COMMENT '发件人',
  `sender_name`      VARCHAR(64)  DEFAULT NULL,
  `recipient_id`     BIGINT NOT NULL COMMENT '收件人',
  `recipient_name`   VARCHAR(64)  DEFAULT NULL,
  `content`          TEXT,
  `is_read`          TINYINT      NOT NULL DEFAULT 0 COMMENT '收件人是否已读',
  `read_time`        DATETIME     DEFAULT NULL,
  `sender_deleted`   TINYINT      NOT NULL DEFAULT 0,
  `recipient_deleted` TINYINT     NOT NULL DEFAULT 0,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_inbox`  (`recipient_id`, `is_read`),
  KEY `idx_thread` (`sender_id`, `recipient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息';

-- ---------- 消息附件 ----------
CREATE TABLE IF NOT EXISTS `message_attachment` (
  `id`           BIGINT       NOT NULL,
  `message_id`   BIGINT       NOT NULL,
  `file_name`    VARCHAR(255) NOT NULL,
  `stored_name`  VARCHAR(128) NOT NULL,
  `content_type` VARCHAR(128)          DEFAULT NULL,
  `size`         BIGINT                DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息附件';

-- ---------- 菜单与权限: 消息中心(顶级页) ----------
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_name`,`perm_key`,`type`,`path`,`component`,`icon`,`sort`,`status`,`create_time`) VALUES
  (1100, 0,    '消息中心', 'message:list', 'C', '/message', 'message/index', 'ChatDotRound', 12, 1, NOW()),
  (1101, 1100, '发送消息', 'message:send', 'B', NULL, NULL, NULL, 1, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`), `path` = VALUES(`path`),
                        `component` = VALUES(`component`), `icon` = VALUES(`icon`);

-- ---------- 授权 ----------
-- 超管: 全部 (幂等)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 消息中心(1100/1101) 给全部业务角色 (全员可收发消息)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT t.rid, t.pid FROM (
  SELECT r.id AS rid, p.id AS pid
  FROM `sys_role` r CROSS JOIN `sys_permission` p
  WHERE r.role_key IN ('salesman','sales_manager','finance','readonly')
    AND p.perm_key IN ('message:list','message:send')
) t
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
