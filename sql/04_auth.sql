-- =====================================================================
-- 注册/登录增强 - 邮箱唯一约束
-- 加载: mysql -u root -proot --default-character-set=utf8mb4 < sql/04_auth.sql
-- 说明: 试运营版 (trial-v1.0) 收紧"新用户默认权限":
--       新注册用户默认 readonly 角色, 但 readonly **不再预授任何业务菜单**,
--       登录后只有"首页 + 消息中心"(消息权限在 16_collab.sql 授予), 其余业务/财务
--       数据一律需由超级管理员在"用户管理/角色管理"中分配后才可见。
--       因此本文件不再给 readonly 批量授予业务浏览权限。
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

-- (试运营: 已移除 readonly -> 全部业务菜单 的批量授权, 改为最小可见。
--  如需恢复"只读可浏览全部模块", 可从 git 历史取回本段 INSERT..SELECT。)

-- 邮箱唯一约束(含逻辑删除维度)。
-- 注意: 若该索引已存在, 重复执行本句会报 "Duplicate key name 'uk_email'", 可安全忽略。
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_email` (`email`, `deleted`);
