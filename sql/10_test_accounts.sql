-- =====================================================================
-- Legacy trial test account cleanup.
-- This script no longer creates test accounts and no longer grants role
-- permissions. Built-in role permissions are managed by:
--   sql/35_role_permission_structure.sql
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

DELETE FROM `sys_user_role` WHERE `user_id` BETWEEN 90002 AND 90006;
DELETE FROM `sys_user` WHERE `id` BETWEEN 90002 AND 90006;
