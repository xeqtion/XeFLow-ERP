-- =====================================================================
-- 用户管理闭环 - 新增"重置密码"按钮权限, 并将全部权限重新授予超级管理员
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 < sql/05_user_mgmt.sql
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- 重置密码 按钮权限 (挂在 用户管理 901 下)
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_key`, `type`, `sort`, `status`, `create_time`) VALUES
  (9014, 901, '重置密码', 'system:user:resetPwd', 'B', 4, 1, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- 超级管理员角色 -> 全部权限 (含上面新增项, 幂等)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
