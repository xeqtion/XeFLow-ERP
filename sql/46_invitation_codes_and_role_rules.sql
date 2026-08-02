-- =====================================================================
-- 管理员邀请码 + 用户多角色约束
-- 1) 邀请码支持有效期与可用次数
-- 2) 超级管理员仅限 admin；管理员角色独占；只读角色与其他角色互斥
-- 3) 其他角色最多保留三个
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_invitation_code` (
  `id`          BIGINT       NOT NULL,
  `invite_code` VARCHAR(32)  NOT NULL COMMENT '系统自动生成的邀请码',
  `expire_time` DATETIME              DEFAULT NULL COMMENT '失效时间，NULL 表示永久有效',
  `max_uses`    INT          NOT NULL DEFAULT 1 COMMENT '最大可用次数',
  `used_count`  INT          NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `create_by`   BIGINT                DEFAULT NULL,
  `create_time` DATETIME              DEFAULT NULL,
  `update_by`   BIGINT                DEFAULT NULL,
  `update_time` DATETIME              DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_invite_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户注册邀请码';

-- 兼容已经执行过本迁移的环境：允许用 NULL 表示永久有效。
ALTER TABLE `sys_invitation_code`
  MODIFY COLUMN `expire_time` DATETIME NULL DEFAULT NULL COMMENT '失效时间，NULL 表示永久有效';

INSERT INTO `sys_permission`
  (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`)
VALUES
  (9015, 901, '查看邀请码', 'system:user:invite:list',   'B', 5, 1, NOW()),
  (9016, 901, '生成邀请码', 'system:user:invite:create', 'B', 6, 1, NOW()),
  (9017, 901, '删除邀请码', 'system:user:invite:delete', 'B', 7, 1, NOW())
ON DUPLICATE KEY UPDATE
  `perm_name` = VALUES(`perm_name`),
  `perm_key` = VALUES(`perm_key`),
  `status` = 1;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`id` IN (9015, 9016, 9017)
WHERE r.`role_key` IN ('super_admin', 'admin')
  AND r.`deleted` = 0;

-- 超级管理员只允许内置 admin(id=1) 持有。
DELETE FROM `sys_user_role` WHERE `role_id` = 0 AND `user_id` <> 1;
DELETE FROM `sys_user_role` WHERE `user_id` = 1 AND `role_id` <> 0;
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 1, 0 FROM `sys_user` WHERE `id` = 1 AND `deleted` = 0;

-- 管理员角色已包含全部权限，因此移除同一用户的其他角色。
DELETE ur
FROM `sys_user_role` ur
JOIN `sys_user_role` admin_role
  ON admin_role.`user_id` = ur.`user_id` AND admin_role.`role_id` = 1
WHERE ur.`role_id` <> 1;

-- 只读角色不能与其他角色共存；已有其他角色时移除只读角色。
DELETE readonly_role
FROM `sys_user_role` readonly_role
JOIN `sys_user_role` other_role
  ON other_role.`user_id` = readonly_role.`user_id`
 AND other_role.`role_id` <> 5
WHERE readonly_role.`role_id` = 5;

-- 清理历史上超过三个角色的异常组合，优先保留内置角色 ID 较小的三个。
DELETE ur
FROM `sys_user_role` ur
JOIN (
  SELECT `user_id`, `role_id`
  FROM (
    SELECT `user_id`, `role_id`,
           ROW_NUMBER() OVER (PARTITION BY `user_id` ORDER BY `role_id`) AS rn
    FROM `sys_user_role`
  ) ranked
  WHERE ranked.rn > 3
) extra ON extra.`user_id` = ur.`user_id` AND extra.`role_id` = ur.`role_id`;
